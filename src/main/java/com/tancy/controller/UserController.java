package com.tancy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tancy.enums.OperatorFriendRequestTypeEnum;
import com.tancy.enums.SearchFriendsStatusEnum;
import com.tancy.pojo.ChatMsg;
import com.tancy.pojo.Users;
import com.tancy.pojo.bo.UsersBO;
import com.tancy.pojo.vo.MyFriendsVO;
import com.tancy.utils.FastdfsClient;
import com.tancy.utils.FileUtils;
import com.tancy.utils.MD5Utils;
import com.tancy.pojo.vo.UsersVO;
import com.tancy.service.UserService;
import com.tancy.utils.JSONResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("u")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private FastdfsClient fastdfsClient;

    @PostMapping("/registOrLogin")
    public JSONResult registOrLogin(@RequestBody Users user) throws Exception{

        //判断用户名和密码不能为空
        if(StringUtils.isBlank(user.getUsername())
                || StringUtils.isBlank(user.getPassword())) {
            return JSONResult.errorMsg("用户名或密码不能为空");
        }

        boolean usernameIsExist = userService.queryUsernameIsExist(user.getUsername());
        Users userResult = null;
        if(usernameIsExist) {
            //登录
            userResult = userService.queryUserForLogin(user.getUsername(),
                    MD5Utils.getMD5Str(user.getPassword()));

            if(userResult == null) {
                return JSONResult.errorMsg("用户名或密码不正确");
            }

        } else {
            //注册并返回user到前端
            userResult = userService.saveUser(user);
        }

        //将users转换为usersVO返回前端
        UsersVO usersVO = new UsersVO();
        BeanUtils.copyProperties(userResult,usersVO);

        return JSONResult.ok(usersVO);
    }

    @PostMapping("/uploadFaceBase64")
    public JSONResult uploadFaceBase64(@RequestBody UsersBO usersBO) throws Exception{

        //获取前端传过来的base64字符串，然后转换为文件对象再上传
        String base64Data = usersBO.getFaceData();
        //本地临时存贮，将字符串变为file
        String userFacePath = "F:\\" + usersBO.getUserId() + "userface64.png";

        FileUtils.base64ToFile(userFacePath,base64Data);

        //上传文件到fastdfs
        MultipartFile faceFile = FileUtils.fileToMultipart(userFacePath);

        //大图的图片路径
        String url = fastdfsClient.uploadBase64(faceFile);
        System.out.println(url);

        //小图的图片路径
        String thump = "_80x80.";
        String arr[] = url.split("\\.");
        String thumpImgUrl = arr[0] + thump + arr[1];

        //把信息写入数据库
        Users user = new Users();
        user.setId(usersBO.getUserId());
        user.setFaceImage(thumpImgUrl);
        user.setFaceImageBig(url);

        Users userRes = userService.updateUserInfo(user);

        UsersVO usersVO = new UsersVO();
        BeanUtils.copyProperties(userRes,usersVO);

        return JSONResult.ok(usersVO);

    }

    @PostMapping("/setNickname")
    public JSONResult setNickname(@RequestBody UsersBO usersBO) throws Exception {

        Users user = new Users();
        user.setId(usersBO.getUserId());
        user.setNickname(usersBO.getNickname());

        Users userRes = userService.updateUserInfo(user);

        UsersVO usersVO = new UsersVO();
        BeanUtils.copyProperties(userRes,usersVO);

        return JSONResult.ok(usersVO);
    }

    @PostMapping("/search")
    public JSONResult searchUser(String myUserId, String friendUsername) throws Exception{

        if(StringUtils.isBlank(myUserId)
                || StringUtils.isBlank(friendUsername)){
            return JSONResult.errorMsg("信息不能为空！");
        }

        // 前置条件 - 1. 搜索的用户不存在，返回[无此用户]
        // 前置条件 - 2. 搜索账号是你自己，返回[不能添加自己]
        // 前置条件 - 3. 搜索的朋友已经是你的好友，返回[该用户已经是你的好友]

        Integer status = userService.preconditionSearchFriends(myUserId,friendUsername);
        if(status == SearchFriendsStatusEnum.SUCCESS.status) {
            Users user = userService.queryUserInfoByUsername(friendUsername);
            UsersVO userVO = new UsersVO();
            BeanUtils.copyProperties(user,userVO);
            return JSONResult.ok(userVO);
        } else {
            String errormsg = SearchFriendsStatusEnum.getMsgByKey(status);
            return JSONResult.errorMsg(errormsg);
        }

    }

    @PostMapping("/addFriendRequest")
    public JSONResult addFriendRequest(String myUserId, String friendUsername) throws Exception {

        if(StringUtils.isBlank(myUserId)
                || StringUtils.isBlank(friendUsername)){
            return JSONResult.errorMsg("信息不能为空！");
        }

        // 前置条件 - 1. 搜索的用户不存在，返回[无此用户]
        // 前置条件 - 2. 搜索账号是你自己，返回[不能添加自己]
        // 前置条件 - 3. 搜索的朋友已经是你的好友，返回[该用户已经是你的好友]
        Integer status = userService.preconditionSearchFriends(myUserId,friendUsername);
        if(status == SearchFriendsStatusEnum.SUCCESS.status) {

            userService.sendFriendRequest(myUserId,friendUsername);

        } else {
            String errormsg = SearchFriendsStatusEnum.getMsgByKey(status);
            return JSONResult.errorMsg(errormsg);
        }

        return JSONResult.ok();

    }

    @PostMapping("/queryFriendRequests")
    public JSONResult queryFriendRequests(String userId) {

        //判断不能为空
        if (StringUtils.isBlank(userId)){
            return JSONResult.errorMsg("");
        }

        //查询用户接收到的朋友申请
        return JSONResult.ok(userService.queryFriendRequestList(userId));
    }

    @PostMapping("/operFriendRequest")
    public JSONResult operFriendRequest(String acceptUserId, String sendUserId,
                                        Integer operType) {

        // 判断不能为空
        if (StringUtils.isBlank(acceptUserId) || StringUtils.isBlank(sendUserId)
                || operType == null){
            return JSONResult.errorMsg("");
        }

        // operType只能为0或者1
        if (StringUtils.isBlank(OperatorFriendRequestTypeEnum.getMsgByType(operType))) {
            return JSONResult.errorMsg("");
        }

        if (operType == OperatorFriendRequestTypeEnum.IGNORE.type) {
            // 判断如果忽略好友请求，则删除好友请求的记录
            userService.deleteFriendRequest(sendUserId,acceptUserId);
        } else if (operType == OperatorFriendRequestTypeEnum.PASS.type) {
            // 添加好友记录，且删除好友请求记录
            userService.passFriendRequest(sendUserId,acceptUserId);
        }

        List<MyFriendsVO> myFriends = userService.queryMyFriends(acceptUserId);

        return JSONResult.ok(myFriends);
    }

    /**
     * 查询好友列表
     * @param userId
     * @return
     */
    @PostMapping("/myFriends")
    public JSONResult myFriends(String userId) {

        if (StringUtils.isBlank(userId)) {
            return JSONResult.errorMsg("");
        }

        List<MyFriendsVO> myFriends = userService.queryMyFriends(userId);
        return JSONResult.ok(myFriends);
    }

    /**
     *  用于手机端获取未签收的消息列表
     * @param acceptUserId
     * @return
     */
    @PostMapping("/getUnReadMsgList")
    public JSONResult getUnReadMsgList(String acceptUserId) {

        if (StringUtils.isBlank(acceptUserId)) {
            return JSONResult.errorMsg("");
        }

        List<com.tancy.pojo.ChatMsg> unReadMsgList = userService.getUnReadMsgList(acceptUserId);

        return JSONResult.ok(unReadMsgList);
    }


}
