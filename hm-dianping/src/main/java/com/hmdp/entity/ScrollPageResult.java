package com.hmdp.entity;

import lombok.Data;

import java.util.List;

/**
 * @author: piggy
 * @date: 2025/6/4 21:51
 * @version: 1.0
 */

@Data
public class ScrollPageResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
