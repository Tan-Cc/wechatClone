package com.tancy.mapper;

import com.tancy.pojo.Users;
import com.tancy.pojo.vo.FriendRequestVO;
import com.tancy.pojo.vo.MyFriendsVO;
import com.tancy.utils.MyMapper;

import java.util.List;

public interface UsersMapperCustom extends MyMapper<Users> {

    List<FriendRequestVO> queryFriendRequestList(String acceptUserId);

    List<MyFriendsVO> queryMyFriends(String userId);

    void batchUpdateMsgSigned(List<String> msgIdList);
}