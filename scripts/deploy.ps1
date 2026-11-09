# 一键部署：本机构建 -> 上传 -> 备份 -> 切换 -> 自检
#
# 用法（在本机项目根目录）：
#   .\scripts\deploy.ps1                 # 完整：clean 打包 + 前端构建 + 部署
#   .\scripts\deploy.ps1 -SkipBuild      # 跳过构建，直接部署 target 里现成的 jar
#   .\scripts\deploy.ps1 -Server 1.2.3.4 -Key C:\Users\me\.ssh\other_key
#
# 如果提示"禁止运行脚本"（执行策略限制），这样跑：
#   powershell -ExecutionPolicy Bypass -File .\scripts\deploy.ps1
#
# 前提：本机能用密钥免密 ssh 到服务器（第一次见 scripts/README 或问 DSH）。
# 只替换 app.jar 和 web 静态文件，**不动数据库**。

param(
    [string]$Server = "47.242.7.45",
    [string]$Key = "$env:USERPROFILE\.ssh\kb_deploy",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Die($msg) { Write-Host "FAILED: $msg" -ForegroundColor Red; exit 1 }

# ---------- 0. 前置检查 ----------
if (-not (Test-Path $Key)) { Die "找不到私钥 $Key" }
$ssh = { param($cmd) & ssh -i $Key -o BatchMode=yes -o ConnectTimeout=15 root@$Server $cmd }
& $ssh "echo ok" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Die "ssh 连不上 $Server（检查密钥/安全组）" }

$dirty = git status --porcelain
if ($dirty) { Write-Host "注意：工作区有未提交改动，部署的是当前文件内容" -ForegroundColor Yellow }

# ---------- 1. 构建 ----------
if (-not $SkipBuild) {
    # 顺序很重要：先出前端产物、同步进 static，最后再 clean package 一次。
    # 反过来先打包，jar 里会带上一版的前端；而且 clean 不会被跳过的旧资源污染。
    Step "构建前端"
    Push-Location frontend
    npm run build
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { Die "前端构建失败" }

    Step "前端产物同步到 static"
    $dst = "src\main\resources\static"
    Get-ChildItem $dst -Force | Remove-Item -Recurse -Force
    Copy-Item -Path "frontend\dist\*" -Destination $dst -Recurse -Force

    Step "构建后端（clean package）"
    & .\mvnw.cmd -q -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { Die "Maven 打包失败" }
}

$jar = Get-ChildItem target -Filter *.jar | Select-Object -First 1
if (-not $jar) { Die "target 下没有 jar，先去掉 -SkipBuild" }

# ---------- 2. 上传 ----------
Step "上传（$([math]::Round($jar.Length/1MB)) MB）"
& $ssh "rm -rf /tmp/web.new /tmp/app.new.jar; mkdir -p /tmp/web.new; echo ready" | Out-Null
& scp -i $Key -o BatchMode=yes $jar.FullName root@${Server}:/tmp/app.new.jar
if ($LASTEXITCODE -ne 0) { Die "jar 上传失败" }
& scp -i $Key -o BatchMode=yes -r "frontend\dist\*" root@${Server}:/tmp/web.new/
if ($LASTEXITCODE -ne 0) { Die "前端上传失败" }

$localMd5 = (Get-FileHash $jar.FullName -Algorithm MD5).Hash.ToLower()
$remoteMd5 = (& $ssh "md5sum /tmp/app.new.jar" | Out-String).Trim().Split(' ')[0]
if ($localMd5 -ne $remoteMd5) { Die "jar 校验不一致：本地 $localMd5 远端 $remoteMd5" }

# ---------- 3. 切换 ----------
Step "备份并切换"
# 远端脚本用数组拼、写成临时文件再传上去：
# 直接 pipe 给 ssh 时 PowerShell 会把最后一行换成 CRLF，bash 收到 '3\r' 这种参数就报错
$remote = @(
    'TS=$(date +%Y%m%d-%H%M%S)'
    'systemctl stop kb'
    'cp -a /opt/kb/app.jar /opt/kb/app.jar.bak-$TS'
    'mv /opt/kb/web /opt/kb/web.bak-$TS'
    'mv /tmp/web.new /opt/kb/web'
    'cp /tmp/app.new.jar /opt/kb/app.jar'
    'chown -R root:root /opt/kb/web /opt/kb/app.jar'
    'find /opt/kb/web -type d -exec chmod 755 {} +'
    'find /opt/kb/web -type f -exec chmod 644 {} +'
    'chmod 644 /opt/kb/app.jar'
    'systemctl start kb'
    'for i in $(seq 1 60); do'
    '  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 http://127.0.0.1:8081/api/auth/me || true)'
    '  [ "$code" = "401" ] && break'
    '  sleep 1'
    'done'
    'echo "health=$code"'
    'systemctl is-active kb'
    'ls -1 /opt/kb | grep bak | tail -n 3'
) -join "`n"

$sh = Join-Path $env:TEMP ("kb-deploy-" + [guid]::NewGuid().ToString("N") + ".sh")
[System.IO.File]::WriteAllText($sh, $remote + "`n", (New-Object System.Text.UTF8Encoding($false)))
& scp -i $Key -o BatchMode=yes $sh root@${Server}:/tmp/kb-deploy.sh
if ($LASTEXITCODE -ne 0) { Die "远端脚本上传失败" }

& ssh -i $Key -o BatchMode=yes root@$Server "bash /tmp/kb-deploy.sh"
if ($LASTEXITCODE -ne 0) { Die "远端切换失败（服务可能已停，立刻 ssh 上去看 journalctl -u kb）" }

# ---------- 4. 自检 ----------
Step "公网自检"
$root_code = & curl.exe -s -o NUL -w "%{http_code}" --max-time 20 "http://$Server/"
$me_code = & curl.exe -s -o NUL -w "%{http_code}" --max-time 20 "http://$Server/api/auth/me"
$js = (& curl.exe -s --max-time 20 "http://$Server/" | Out-String) -replace "`r", ""
$match = [regex]::Match($js, '/assets/(index-[^"]+\.js)')
$jsCode = if ($match.Success) { & curl.exe -s -o NUL -w "%{http_code}" --max-time 20 ("http://$Server/assets/" + $match.Groups[1].Value) } else { "no-asset" }

Write-Host "首页=$root_code  未登录接口=$me_code  前端JS=$jsCode ($($match.Groups[1].Value))"
if ($root_code -ne "200" -or $me_code -ne "401" -or $jsCode -ne "200") {
    Die "自检没通过，先别离开"
}
Write-Host "`n部署成功：http://$Server/" -ForegroundColor Green
