/**
 * 内存中的登录态缓存。
 *
 * 真正的凭证是浏览器 Cookie 里的 JSESSIONID，这里的变量只是"省一次请求"：
 * 已经问过后端并确认登录成功，就不用每次跳页面都再问一遍。
 *
 * 为什么不用 localStorage：localStorage 可以随手改成 '1'，而且它和后端的
 * Session 完全脱钩，后端一重启就假登录了。
 */

let checked = false   // 是否已经向后端确认过
let verified = false  // 确认的结果：true 已登录，false 未登录

export const hasChecked = () => checked
export const isVerified = () => verified

// 登录成功 / 确认已登录时调用
export const markLoggedIn = () => {
  checked = true
  verified = true
}

// 退出、或任何请求返回 401 时调用
export const markLoggedOut = () => {
  checked = true
  verified = false
}
