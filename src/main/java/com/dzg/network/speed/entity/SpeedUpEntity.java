package com.dzg.network.speed.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * @auther: dingzhenggang
 * @since: V1.0 2019-04-16
 */
@Accessors(chain = true)
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class SpeedUpEntity {

  private String session;
  private String secret;
  private Boolean loop;
}
