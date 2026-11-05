package com.example.kb.mapper;

import com.example.kb.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * user 表的数据访问入口。
 *
 * 只保留当前真正用得上的四个方法，不预先写 findAll / delete 之类的方法
 * —— 多一个方法就多一个可能被误调用的口子。
 */
@Mapper
public interface UserMapper {

    /** 按登录名查用户。登录、注册查重都走它（username 上有唯一索引） */
    User findByUsername(@Param("username") String username);

    /** 按主键查用户。session 里只存 id，需要回查完整信息（/me 用） */
    User findById(@Param("id") Long id);

    /**
     * 插入新用户。
     * 配合 XML 里的 useGeneratedKeys，插入成功后自增 id 会回填到 user.id，
     * 这样注册接口能立刻把 id 返回给前端。
     */
    void insert(User user);

    /** 只更新 agent_rule 一列，不做全字段 UPDATE，避免覆盖用户其它信息 */
    void updateAgentRule(@Param("id") Long id, @Param("agentRule") String agentRule);
}
