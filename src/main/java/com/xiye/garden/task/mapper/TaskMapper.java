package com.xiye.garden.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiye.garden.task.entity.TaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {
    @Select("SELECT * FROM tasks WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    TaskEntity selectForUpdate(@Param("id") long id);
    @Update("UPDATE tasks SET deleted = 1, updated_at = #{updatedAt} WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("id") long id, @Param("updatedAt") LocalDateTime updatedAt);
}
