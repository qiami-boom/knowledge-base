package com.example.kb.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码工具。
 *
 * 只做两件事：加密（注册存库用）、校验（登录比对用）。
 * 无状态，所以做成静态工具类，不需要注册成 Spring Bean。
 */
public final class PasswordHasher {

    /** BCrypt 编码器无状态，建一个反复用，不必每个请求都 new */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 私有构造器：只提供静态方法，不允许被 new */
    private PasswordHasher() {
    }

    /**
     * 加密：注册时调用。
     * BCrypt 每次都会生成随机盐，所以同一个密码每次 encode 出来的结果都不同。
     */
    public static String encode(String raw) {
        return ENCODER.encode(raw);
    }

    /**
     * 校验：登录时调用。
     *
     * 注意：不能用 encode(明文).equals(库里的 hash) —— 盐是随机的，永远不相等。
     * 必须用 matches，它会取出 hash 里的盐重新算一遍再比对。
     */
    public static boolean matches(String raw, String hash) {
        // 脏数据兜底：hash 为 null/空时直接判失败，避免抛异常变成 500
        if (raw == null || hash == null || hash.isBlank()) {
            return false;
        }
        return ENCODER.matches(raw, hash);
    }
}
