package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;

public interface IKernelGroupService {
    void setMemberShutUp(long groupCode, ArrayList<GroupMemberShutUpInfo> list, IOperateCallback callback);

    void setGroupShutUp(long groupCode, boolean shutUp, IOperateCallback callback);

    void getGroupBulletinList(long groupCode, String from, String seq, GroupBulletinListReq req, IOperateCallback callback);
}
