package com.bergerkiller.bukkit.tc.signactions;

import java.util.Locale;

import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

public class SignActionFlip extends TrainCartsSignAction {

    public SignActionFlip() {
        super("flip");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isPowered()) {
            return;
        }

        boolean perCart = info.getLine(1).toLowerCase(Locale.ENGLISH).contains("percart");
        if (info.isTrainSign() && info.hasGroup() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
            if (perCart) {
                for (MinecartMember<?> member : info.getGroup()) {
                    member.flipOrientation();
                }
            } else {
                info.getGroup().flipOrientation();
            }
        } else if (info.isCartSign() && info.hasMember() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
            info.getMember().flipOrientation();
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                if (perCart) {
                    for (MinecartMember<?> member : group) {
                        member.flipOrientation();
                    }
                } else {
                    group.flipOrientation();
                }
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_FLIPPER)
                .setName(event.isCartSign() ? "cart flipper" : "train cart flipper");

        if (event.isTrainSign()) {
            opt.setDescription("flip the orientation of all Minecarts in a train");
        } else if (event.isCartSign()) {
            opt.setDescription("flip the orientation of a Minecart");
        } else if (event.isRCSign()) {
            opt.setDescription("flip the orientation of all Minecarts in a train remotely");
        }
        return opt.handle(event);
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
