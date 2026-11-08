# 数据隔离验收脚本
# 用法：后端启动后，任意目录执行：
#   .\scripts\acceptance.ps1
# 全部 PASS 才算数据隔离达标。任何一条 FAIL 都是跨用户访问漏洞。
#
# 脚本会自动创建（或复用）两个账号 accA / accB，不碰你的演示账号。

$base = "http://localhost:8081"
$script:pass = 0
$script:fail = 0

function Check($name, $ok, $detail = "") {
    if ($ok) {
        $script:pass++
        Write-Host "[PASS] $name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "[FAIL] $name  ->  $detail" -ForegroundColor Red
    }
}

function GetSession($user, $pwd) {
    $body = @{ username = $user; password = $pwd } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post `
            -ContentType "application/json" -Body $body -SessionVariable s | Out-Null
    } catch {
        # 账号不存在，注册后再登录
        Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post `
            -ContentType "application/json" -Body $body | Out-Null
        Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post `
            -ContentType "application/json" -Body $body -SessionVariable s | Out-Null
    }
    return $s
}

Write-Host "== 准备两个账号 ==" -ForegroundColor Cyan
$sessA = GetSession "accA" "12345678"
$sessB = GetSession "accB" "12345678"
Check "账号 A 登录" ($sessA.Cookies.Count -gt 0) "没有拿到 session cookie"
Check "账号 B 登录" ($sessB.Cookies.Count -gt 0) "没有拿到 session cookie"

Write-Host "`n== 写入归属 ==" -ForegroundColor Cyan
$title = "acceptance-" + (Get-Date -Format "yyyyMMddHHmmss")
$body = @{ title = $title; content = "content for isolation test" } | ConvertTo-Json
$created = Invoke-RestMethod -Uri "$base/api/knowledges" -Method Post `
    -ContentType "application/json" -Body $body -WebSession $sessA
$idA = $created.data.id
Check "A 新增一条知识" ($null -ne $idA) "新增失败"
Check "新增记录带 user_id" ($null -ne $created.data.userId) "user_id 是 null，写入侧没有填 userId"

Write-Host "`n== 列表隔离 ==" -ForegroundColor Cyan
$listA = Invoke-RestMethod -Uri "$base/api/knowledges" -WebSession $sessA
$listB = Invoke-RestMethod -Uri "$base/api/knowledges" -WebSession $sessB
$inA = @($listA.data | Where-Object { $_.id -eq $idA }).Count
$inB = @($listB.data | Where-Object { $_.id -eq $idA }).Count
Check "A 看得到自己的" ($inA -gt 0) "A 的列表里没有刚新增的那条"
Check "B 看不到 A 的" ($inB -eq 0) "B 的列表里出现了 A 的数据"

Write-Host "`n== 越权访问 ==" -ForegroundColor Cyan

# 越权读：必须 404，返回 200 或 403 都算失败
try {
    Invoke-RestMethod -Uri "$base/api/knowledges/$idA" -WebSession $sessB | Out-Null
    Check "B 越权读返回 404" $false "实际返回 200（数据泄露）"
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    Check "B 越权读返回 404" ($code -eq 404) "实际返回 $code；403 会泄露'这条存在'"
}

# 越权改
try {
    $updateBody = @{ title = "hacked"; content = "hacked" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$base/api/knowledges/$idA" -Method Put `
        -ContentType "application/json" -Body $updateBody -WebSession $sessB | Out-Null
    Check "B 越权修改返回 404" $false "实际返回 200（数据被改）"
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    Check "B 越权修改返回 404" ($code -eq 404) "实际返回 $code"
}

# 越权删
try {
    Invoke-RestMethod -Uri "$base/api/knowledges/$idA" -Method Delete -WebSession $sessB | Out-Null
    Check "B 越权删除返回 404" $false "实际返回 200（数据被删）"
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    Check "B 越权删除返回 404" ($code -eq 404) "实际返回 $code"
}

Write-Host "`n== 未登录 ==" -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$base/api/knowledges" | Out-Null
    Check "未登录返回 401" $false "实际返回 200（不需要登录就能读）"
} catch {
    $code = [int]$_.Exception.Response.StatusCode
    Check "未登录返回 401" ($code -eq 401) "实际返回 $code"
}

# 确认 A 的数据还在（上面的越权操作没有真的删掉）
try {
    $checkA = Invoke-RestMethod -Uri "$base/api/knowledges/$idA" -WebSession $sessA
    Check "A 的数据未被越权破坏" ($checkA.data.id -eq $idA) "A 的数据已被改动"
} catch {
    Check "A 的数据未被越权破坏" $false "A 的数据已经不在了（被越权删除）"
}

Write-Host ("`n== 结果：PASS {0} / FAIL {1} ==" -f $script:pass, $script:fail) `
    -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })

if ($script:fail -gt 0) { exit 1 } else { exit 0 }
