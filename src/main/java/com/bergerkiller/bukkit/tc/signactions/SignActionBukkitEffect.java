package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.utils.Effect;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

import org.bukkit.entity.Player;

/**
 * Old effect sign that is specifically made to play Bukkit Sound and Effect enum effects.
 * It's kind of awful. But will stay around until we've made all sounds and particles available
 * in the attachments system.
 */
public class SignActionBukkitEffect extends TrainCartsSignAction {

    public static Effect parse(SignActionEvent event) {
        Effect eff = new Effect();
        eff.parseEffect(event.getLine(2));
        eff.parseEffect(event.getLine(3));
        String[] args = StringUtil.getAfter(event.getLine(1), " ").trim().split(" ", -1);
        try {
            if (args.length >= 1) {
                eff.pitch = (float) ParseUtil.parseDouble(args[0], 1.0);
            }
            if (args.length == 2) {
                eff.volume = (float) ParseUtil.parseDouble(args[1], 1.0);
            }
        } catch (NumberFormatException ignored) {
        }
        return eff;
    }

    public SignActionBukkitEffect() {
        super("beffect", "meffect", "peffect");
    }

    @Override
    public void execute(SignActionEvent info) {
        boolean move = info.isType("meffect");
        boolean player = info.isType("peffect");
        if (!info.isPowered()) return;
        Effect eff = parse(info);
        if (info.isAction(SignActionType.MEMBER_MOVE)) {
            if (move) {
                if (info.isTrainSign()) {
                    for (MinecartMember<?> member : info.getGroup()) {
                        eff.play(member.getEntity().getLocation());
                    }
                } else if (info.isCartSign()) {
                    eff.play(info.getMember().getEntity().getLocation());
                }
            }
            return;
        }
        if(player) {
            if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER)) {
                for (MinecartMember<?> member : info.getGroup()) {
                    for(Player p : member.getEntity().getPlayerPassengers()) {
                        eff.play(p);
                    }
                }
            } else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER)) {
                for(Player p : info.getMember().getEntity().getPlayerPassengers()) {
                    eff.play(p);
                }
            }
            return;
        }
        if (info.isTrainSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.GROUP_ENTER) && info.hasGroup()) {
            for (MinecartMember<?> member : info.getGroup()) {
                eff.play(member.getEntity().getLocation());
            }
        } else if (info.isCartSign() && info.isAction(SignActionType.REDSTONE_ON, SignActionType.MEMBER_ENTER) && info.hasMember()) {
            eff.play(info.getMember().getEntity().getLocation());
        } else if (info.isRCSign() && info.isAction(SignActionType.REDSTONE_ON)) {
            for (MinecartGroup group : info.getRCTrainGroups()) {
                for (MinecartMember<?> member : group) {
                    eff.play(member.getEntity().getLocation());
                }
            }
        } else if (info.isAction(SignActionType.REDSTONE_ON)) {
            if (info.hasRails()) {
                eff.play(info.getCenterLocation());
            } else {
                eff.play(info.getLocation().add(0.0, 2.0, 0.0));
            }
        }
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        String app = event.isType("meffect") ? " while moving" : "";

        SignBuildOptions opt = SignBuildOptions.create()
                .setPermission(Permission.BUILD_EFFECT)
                .setName(event.isCartSign() ? "cart Bukkit effect player" : "train Bukkit effect player")
                .setTraincartsWIKIHelp("TrainCarts/Signs/Effect");

        if (event.isTrainSign()) {
            opt.setDescription("play a Bukkit effect in all minecarts of the train" + app);
        } else if (event.isCartSign()) {
            opt.setDescription("play a Bukkit effect in the minecart" + app);
        } else if (event.isRCSign()) {
            opt.setDescription("play a Bukkit effect in all minecarts of the train" + app);
        }
        return opt.handle(event);
    }

    @Override
    public boolean isMemberMoveHandled(SignActionEvent info) {
        return info.isType("meffect");
    }

    @Override
    public boolean canSupportRC() {
        return true;
    }
}
