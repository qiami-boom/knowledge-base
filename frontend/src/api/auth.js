import request from '../utils/request'

/**
 * 认证接口封装。
 * 后端用 Session 认证：登录成功后由浏览器自动保存 JSESSIONID Cookie，
 * 前端不需要（也不应该）自己存 token。
 */

// 登录：成功后后端会把 JSESSIONID 写进 Cookie
export const login = (data) => request.post('/auth/login', data)

// 注册：注册成功后不会自动登录，需要再调一次 login
export const register = (data) => request.post('/auth/register', data)

// 当前用户：未登录时后端返回 401，用来判断会话是否还有效
export const me = () => request.get('/auth/me')

// 退出：让后端销毁会话
export const logout = () => request.post('/auth/logout')

// 读取当前用户的 AI 行为规则
export const getAgentRule = () => request.get('/user/agent-rule')

// 修改当前用户的 AI 行为规则，agentRule 只能是 EXPLAIN / EXAM / SOCRATIC / CUSTOM
export const updateAgentRule = (agentRule) => request.put('/user/agent-rule', { agentRule })
