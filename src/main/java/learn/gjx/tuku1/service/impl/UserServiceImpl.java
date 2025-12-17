package learn.gjx.tuku1.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.dto.user.UserAddRequest;
import learn.gjx.tuku1.model.dto.user.UserQueryRequest;
import learn.gjx.tuku1.model.dto.user.UserUpdateRequest;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.service.UserService;
import learn.gjx.tuku1.mapper.UserMapper;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author GJX
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2025-11-11 17:59:36
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    /**
     * 盐值，用于密码加密
     */
    private static final String SALT = "tuku1_salt_2025";
    @Override
    public long userRegister(String username, String password, String confirmPassword) {
        //1. 校验参数 不能为空，账号密码的限制等等(这里输出的异常，要使用我们的自定义的错误代码和封装的错误返回）
        // 校验参数是否为空
        if (StrUtil.hasBlank(username, password, confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        // 账号长度限制
        if (username.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        // 密码长度限制
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于6位");
        }
        // 校验两次密码是否一致
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码输入不一致");
        }

        //2. 检查用户账号是否和数据库中已有的重复
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, username);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "账号已存在");
        }

        //3. 密码加密
        String encryptedPassword = getEncrptPassword(password);

        //4. 插入数据到数据库中，注意数据库中有很多要求是非空的，如果没有默认值你就需要给他们设计默认值
        User user = new User();
        user.setUserAccount(username);
        user.setUserPassword(encryptedPassword);
        // 设置默认用户名：用户 + 5位随机字母数字组合
        String randomSuffix = RandomUtil.randomString(5);
        user.setUserName("用户" + randomSuffix);
        // 设置用户角色：0-普通用户
        user.setUserRole(0);
        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误！");
        }
        return user.getId();
    }

    @Override
    public String getEncrptPassword(String userPassword){
        // 加盐，加密
        return DigestUtil.md5Hex(SALT + userPassword);
    }

    @Override
    public LoginUserVO userLogin(String username, String password, HttpServletRequest request) {
        //1. 校验参数
        // 校验参数是否为空
        if (StrUtil.hasBlank(username, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        // 账号长度限制
        if (username.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        // 密码长度限制
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于6位");
        }

        //2. 密码加密
        String encryptedPassword = getEncrptPassword(password);

        //3. 查询数据库中的用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, username);
        queryWrapper.eq(User::getUserPassword, encryptedPassword);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在或密码错误");
        }

        //4. 转换为脱敏的用户信息
        LoginUserVO loginUserVO = userLogin(user);

        //5. 保存用户的登录态（存储脱敏后的用户信息）
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, loginUserVO);

        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(User user) {
        // 将 User 实体转换为 LoginUserVO（脱敏）
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setUserName(user.getUserName());
        loginUserVO.setUserAccount(user.getUserAccount());
        loginUserVO.setUserAvatar(user.getUserAvatar());
        loginUserVO.setUserRole(user.getUserRole());
        loginUserVO.setUserProfile(user.getUserProfile());
        loginUserVO.setCreateTime(user.getCreateTime());
        loginUserVO.setUpdateTime(user.getUpdateTime());
        loginUserVO.setEditTime(user.getEditTime());
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUserName(user.getUserName());
        userVO.setUserAccount(user.getUserAccount());
        userVO.setUserAvatar(user.getUserAvatar());
        userVO.setUserRole(user.getUserRole());
        userVO.setUserProfile(user.getUserProfile());
        userVO.setCreateTime(user.getCreateTime());
        userVO.setUpdateTime(user.getUpdateTime());
        userVO.setEditTime(user.getEditTime());
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (userList == null || userList.isEmpty()) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        //1. 判断是否登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        LoginUserVO loginUserVO = (LoginUserVO) userObj;
        Long userId = loginUserVO.getId();

        //2. 从数据库重新查出来数据（防止用户信息被更新或用户被删除）
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        // 判断用户是否被逻辑删除
        if (user.getIsDelete() != null && user.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户已被删除");
        }

        //3. 返回当前User的信息
        return user;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 1.判断是否登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        // 2.移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        Integer userRole = userQueryRequest.getUserRole();
        String userProfile = userQueryRequest.getUserProfile();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 精确查询
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userRole != null, "userRole", userRole);
        // 模糊查询
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), "asc".equals(sortOrder), sortField);
        return queryWrapper;
    }

    /**
     * 默认密码
     */
    private static final String DEFAULT_PASSWORD = "12345678";

    @Override
    public long addUser(UserAddRequest userAddRequest) {
        //1. 校验参数
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String userAccount = userAddRequest.getUserAccount();
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        // 密码为空则使用默认密码
        String userPassword = userAddRequest.getUserPassword();
        if (StrUtil.isBlank(userPassword)) {
            userPassword = DEFAULT_PASSWORD;
        }
        if (userPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于6位");
        }

        //2. 检查账号是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "账号已存在");
        }

        //3. 构建用户实体
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(getEncrptPassword(userPassword));
        String userName = userAddRequest.getUserName();
        user.setUserName(StrUtil.isBlank(userName) ? "用户" + RandomUtil.randomString(5) : userName);
        user.setUserAvatar(userAddRequest.getUserAvatar());
        Integer userRole = userAddRequest.getUserRole();
        user.setUserRole(userRole != null ? userRole : 0);
        user.setUserProfile(userAddRequest.getUserProfile());

        //4. 保存到数据库
        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建用户失败");
        }
        return user.getId();
    }

    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest) {
        //1. 校验参数
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userUpdateRequest.getId();
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不合法");
        }

        //2. 检查用户是否存在
        User oldUser = this.getById(id);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        //3. 构建更新实体
        User user = new User();
        user.setId(id);
        user.setUserName(userUpdateRequest.getUserName());
        user.setUserAvatar(userUpdateRequest.getUserAvatar());
        user.setUserRole(userUpdateRequest.getUserRole());
        user.setUserProfile(userUpdateRequest.getUserProfile());

        //4. 更新数据库
        boolean result = this.updateById(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新用户失败");
        }
        return true;
    }

    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        //1. 校验参数
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        // 限制每页最大数量，防止恶意请求
        if (pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过50");
        }

        //2. 构建查询条件并分页查询
        QueryWrapper<User> queryWrapper = getQueryWrapper(userQueryRequest);
        Page<User> userPage = this.page(new Page<>(current, pageSize), queryWrapper);

        //3. 转换为 UserVO 分页
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);

        return userVOPage;
    }
}




