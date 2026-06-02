package com.xuecheng.content.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class EditCourseDto extends AddCourseDto{
   @ApiModelProperty(value = "课程id", required = true)
    private Long Id;
}
