package com.springagent.diagnosis.service.impl;

import com.springagent.entity.AppUser;
import com.springagent.mapper.AppUserMapper;
import com.springagent.diagnosis.service.IAppUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author author
 * @since 2026-07-24
 */
@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements IAppUserService {

}
