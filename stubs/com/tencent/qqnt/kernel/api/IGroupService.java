package com.tencent.qqnt.kernel.api;

import com.tencent.qqnt.kernel.nativeinterface.IKickMemberOperateCallback;

import java.util.ArrayList;

public interface IGroupService {
    void kickMember(long groupCode, ArrayList<String> memberUids, boolean isBan, String reason, IKickMemberOperateCallback callback);
}
