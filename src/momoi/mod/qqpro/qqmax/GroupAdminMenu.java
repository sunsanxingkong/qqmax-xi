package momoi.mod.qqpro.qqmax;

import android.content.Context;
import android.view.View;

import com.tencent.qqnt.kernel.api.IGroupService;
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService;
import com.tencent.qqnt.kernel.nativeinterface.IKickMemberOperateCallback;
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback;

import java.util.ArrayList;

/**
 * Long-press a group member's nickname/avatar widget: admin actions menu.
 *  - member mute/unmute: WEB admin API (WebMute) — the kernel's own
 *    setMemberShutUp command is rejected server-side for this product
 *    (-10122), so those items call qun_info/set_group_shutup instead.
 *  - whole-group mute: IKernelGroupService.setGroupShutUp (host's own route)
 *  - kick: IGroupService.kickMember
 * Menu style: the host-style full-screen dark list (StyledMenu).
 */
public final class GroupAdminMenu {

    private static final String[] ITEMS = {
            "禁言 10 分钟", "禁言 1 小时", "禁言 1 天",
            "禁言 7 天", "禁言 30 天", "自定义禁言时长…",
            "解除禁言", "全员禁言", "解除全员禁言", "踢出群聊"
    };
    private static final int[] PRESET_SECONDS = {600, 3600, 86400, 604800, 2592000};
    private static final int[] ITEM_ICONS = {
            android.R.drawable.ic_lock_silent_mode, android.R.drawable.ic_lock_silent_mode,
            android.R.drawable.ic_lock_silent_mode, android.R.drawable.ic_lock_silent_mode,
            android.R.drawable.ic_lock_silent_mode, android.R.drawable.ic_menu_edit,
            android.R.drawable.ic_lock_silent_mode_off, android.R.drawable.ic_lock_lock,
            android.R.drawable.ic_menu_revert, android.R.drawable.ic_menu_close_clear_cancel
    };

    private GroupAdminMenu() {}

    public static void attach(Object widget, Object record) {
        if (widget == null || record == null) return;
        try {
            if (Refl.getInt(record, "chatType", 0) != 2) return;
            String uid = Refl.getString(record, "senderUid");
            long groupCode = Refl.getLong(record, "peerUin", 0L);
            if (uid == null || uid.length() == 0 || groupCode == 0) return;
            long memberUin = Refl.getLong(record, "senderUin", 0L);
            String name = Refl.getString(record, "sendMemberName");
            if (name == null || name.length() == 0) name = Refl.getString(record, "sendNickName");
            if (name == null || name.length() == 0) name = uid;
            Object nick = Refl.call(widget, "getNickWidget");
            if (nick instanceof View) {
                View view = (View) nick;
                Cfg.setAppContext(view.getContext());
                view.setLongClickable(true);
                view.setOnLongClickListener(
                        new AdminLongClick(groupCode, uid, memberUin, name));
            }
        } catch (Throwable th) {
            Kernel.log("GroupAdminMenu.attach failed: " + th);
        }
    }

    private static final class AdminLongClick implements View.OnLongClickListener {
        private final long groupCode;
        private final String uid;
        private final long memberUin;
        private final String name;

        AdminLongClick(long groupCode, String uid, long memberUin, String name) {
            this.groupCode = groupCode;
            this.uid = uid;
            this.memberUin = memberUin;
            this.name = name;
        }

        @Override
        public boolean onLongClick(View v) {
            final Context context = v.getContext();
            try {
                StyledMenu.show(context, "管理成员：" + name, ITEMS, ITEM_ICONS,
                        new StyledMenu.OnPick() {
                            @Override
                            public void onPick(int which) {
                                if (which >= 0 && which < PRESET_SECONDS.length) {
                                    WebMute.mute(context, groupCode, memberUin,
                                            PRESET_SECONDS[which]);
                                } else if (which == 5) {
                                    showCustomDuration(context, groupCode, memberUin);
                                } else if (which == 6) {
                                    WebMute.mute(context, groupCode, memberUin, 0);
                                } else if (which == 7) {
                                    doGroupShutUp(context, groupCode, true);
                                } else if (which == 8) {
                                    doGroupShutUp(context, groupCode, false);
                                } else if (which == 9) {
                                    confirmKick(context, groupCode, uid, name);
                                }
                            }
                        });
            } catch (Throwable th) {
                Kernel.log("menu failed: " + th);
                Kernel.toast(context, "打开管理菜单失败：" + th.getMessage());
            }
            return true;
        }
    }

    private static void showCustomDuration(final Context ctx, final long groupCode,
                                           final long memberUin) {
        DarkInput.show(ctx, "自定义禁言时长", "输入数字，再选单位", "", false, true,
                new DarkInput.OnOk() {
                    @Override
                    public void onOk(String text) {
                        long value;
                        try {
                            value = Long.parseLong(text.trim());
                        } catch (Throwable t) {
                            Kernel.toast(ctx, "请输入数字");
                            return;
                        }
                        if (value <= 0) {
                            Kernel.toast(ctx, "时长必须大于 0");
                            return;
                        }
                        showUnitPicker(ctx, groupCode, memberUin, value);
                    }
                });
    }

    private static void showUnitPicker(final Context ctx, final long groupCode,
                                       final long memberUin, final long value) {
        StyledMenu.show(ctx, "选择单位（数量：" + value + "）",
                new String[]{"分钟", "小时", "天"},
                new int[]{android.R.drawable.ic_menu_recent_history,
                        android.R.drawable.ic_menu_recent_history,
                        android.R.drawable.ic_menu_recent_history},
                new StyledMenu.OnPick() {
                    @Override
                    public void onPick(int which) {
                        long mult = which == 0 ? 60L : which == 1 ? 3600L : 86400L;
                        long seconds = value * mult;
                        if (seconds > Integer.MAX_VALUE) {
                            Kernel.toast(ctx, "时长太大了");
                            return;
                        }
                        WebMute.mute(ctx, groupCode, memberUin, (int) seconds);
                    }
                });
    }

    static void doGroupShutUp(final Context ctx, long groupCode, final boolean shutUp) {
        try {
            IKernelGroupService svc = Kernel.nativeGroup();
            if (svc == null) {
                Kernel.toast(ctx, "群服务不可用，操作失败");
                return;
            }
            svc.setGroupShutUp(groupCode, shutUp, new IOperateCallback() {
                @Override
                public void onResult(int code, String msg) {
                    if (code == 0) {
                        Kernel.toast(ctx, shutUp ? "已开启全员禁言" : "已解除全员禁言");
                    } else {
                        Kernel.toast(ctx, "操作失败(" + code + ")：" + msg);
                    }
                }
            });
        } catch (Throwable th) {
            Kernel.log("doGroupShutUp failed: " + th);
            Kernel.toast(ctx, "操作失败：" + th.getMessage());
        }
    }

    static void confirmKick(final Context ctx, final long groupCode, final String uid,
                            final String name) {
        StyledMenu.show(ctx, "确定将「" + name + "」移出本群？",
                new String[]{"踢出群聊", "取消"},
                new int[]{android.R.drawable.ic_menu_close_clear_cancel,
                        android.R.drawable.ic_menu_revert},
                new StyledMenu.OnPick() {
                    @Override
                    public void onPick(int which) {
                        if (which == 0) doKick(ctx, groupCode, uid);
                    }
                });
    }

    static void doKick(final Context ctx, long groupCode, String uid) {
        try {
            IGroupService svc = Kernel.groupApi();
            if (svc == null) {
                Kernel.toast(ctx, "群服务不可用，踢人失败");
                return;
            }
            ArrayList<String> uids = new ArrayList<String>();
            uids.add(uid);
            svc.kickMember(groupCode, uids, false, "", new IKickMemberOperateCallback() {
                @Override
                public void onResult(int code, String msg, ArrayList results) {
                    if (code == 0) {
                        Kernel.toast(ctx, "已踢出群聊");
                    } else {
                        Kernel.toast(ctx, "踢出失败(" + code + ")：" + msg);
                    }
                }
            });
        } catch (Throwable th) {
            Kernel.log("doKick failed: " + th);
            Kernel.toast(ctx, "踢出失败：" + th.getMessage());
        }
    }
}
