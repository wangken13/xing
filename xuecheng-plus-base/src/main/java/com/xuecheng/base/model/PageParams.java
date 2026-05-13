package com.xuecheng.base.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PageParams {
    private Long pageNo=2L;
    private Long pageSize=1L;
    public PageParams(Long pageNo, Long pageSize){
        this.pageNo=pageNo;
        this.pageSize=pageSize;
    }
    public PageParams(){

    }
}
