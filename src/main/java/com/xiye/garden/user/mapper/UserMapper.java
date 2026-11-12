package com.xiye.garden.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiye.garden.user.entity.UserEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT * FROM garden_user WHERE email = #{email} FOR UPDATE")
    UserEntity lockByEmail(@Param("email") String email);
    @Select("SELECT * FROM garden_user WHERE username = #{username} FOR UPDATE")
    UserEntity lockByUsername(@Param("username") String username);
}
