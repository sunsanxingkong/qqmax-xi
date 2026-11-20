package com.tencent.qqnt.kernel.api.impl;

import com.tencent.qqnt.kernel.api.IGroupService;
import com.tencent.qqnt.kernel.nativeinterface.IKickMemberOperateCallback;

import java.util.ArrayList;

public class GroupService implements IGroupService {
    public Object getService() { return null; }

    @Override
    public void kickMember(long groupCode, ArrayList<String> memberUids, boolean isBan, String reason, IKickMemberOperateCallback callback) {}
}
