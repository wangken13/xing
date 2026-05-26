package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.TeachplanDto;

import java.util.List;

public interface TeachplanService {
    /**
     * @description 查询课程计划树型结构
     * @param courseId 课程id
     * @return List<TeachplanDto>
     * @author
     * @date
     */
    public List<TeachplanDto> findTeachplanTree(long courseId);
}
