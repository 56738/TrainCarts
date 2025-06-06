package com.bergerkiller.bukkit.tc.rails.type;

import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.RailAABB;
import com.bergerkiller.bukkit.tc.controller.components.RailPiece;
import com.bergerkiller.bukkit.tc.controller.components.RailJunction;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;
import com.bergerkiller.bukkit.tc.controller.components.RailPath.Position;
import com.bergerkiller.bukkit.tc.controller.components.RailState;
import com.bergerkiller.bukkit.tc.controller.global.SignControllerWorld;
import com.bergerkiller.bukkit.tc.editor.RailsTexture;
import com.bergerkiller.bukkit.tc.rails.RailLookup;
import com.bergerkiller.bukkit.tc.rails.RailLookup.TrackedSign;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogic;
import com.bergerkiller.bukkit.tc.rails.logic.RailLogicAir;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class RailType {
    public static final RailTypeVertical VERTICAL = new RailTypeVertical();
    public static final RailTypeActivator ACTIVATOR_ON = new RailTypeActivator(true);
    public static final RailTypeActivator ACTIVATOR_OFF = new RailTypeActivator(false);
    public static final RailTypeCrossing CROSSING = new RailTypeCrossing();
    public static final RailTypeRegular REGULAR = new RailTypeRegular();
    public static final RailTypeDetector DETECTOR = new RailTypeDetector();
    public static final RailTypePowered BRAKE = new RailTypePowered(false);
    public static final RailTypePowered BOOST = new RailTypePowered(true);
    public static final RailTypeNone NONE = new RailTypeNone();
    private static List<RailType> values = new ArrayList<RailType>();
    private final boolean _isComplexRailBlock;
    private final boolean _isHandlingPhysics;
    private boolean _registered = false;

    static {
        for (RailType type : CommonUtil.getClassConstants(RailType.class)) {
            type._registered = true; // Including NONE, as it's used as a valid type of rail fallback (air)
            if (type != NONE) {
                values.add(type);
            }
        }
    }

    /**
     * Handles a critical error that occurred while using a certain RailType.
     * If the RailType was externally registered by a plugin, it is unregistered to prevent
     * further failing of TrainCarts itself.
     * 
     * @param railType to unregister
     * @param reason for unregistering
     */
    public static void handleCriticalError(RailType railType, Throwable reason) {
        // If not a registered rail type, ignore the error. We had already disabled this rail.
        if (!values.contains(railType)) {
            return;
        }

        TrainCarts traincarts = TrainCarts.plugin;
        Plugin plugin = CommonUtil.getPluginByClass(railType.getClass());
        Logger logger = traincarts.getLogger();
        if (plugin == traincarts) {
            logger.log(Level.SEVERE, "An error occurred in RailType '" + railType.getClass().getSimpleName() + "'", reason);
        } else if (plugin != null) {
            logger.log(Level.SEVERE, "An error occurred in RailType '" + railType.getClass().getSimpleName() + "' " +
                                     "from plugin " + plugin.getName() + ". The rail type has been disabled.", reason);
            unregister(railType);
        } else {
            logger.log(Level.SEVERE, "An error occurred in RailType '" + railType.getClass().getSimpleName() + "' " + 
                                     "from an unknown plugin. The rail type has been disabled.", reason);
            unregister(railType);
        }
    }

    /**
     * Unregisters a Rail Type so it can no longer be used
     *
     * @param type to unregister
     */
    public static void unregister(RailType type) {
        ArrayList<RailType> newValues = new ArrayList<RailType>(values);
        if (newValues.remove(type)) {
            values = newValues;
            type._registered = false;
            RailLookup.forceUnloadRail(type);
        }
    }

    /**
     * Registers a Rail Type for use
     *
     * @param type         to register
     * @param withPriority - True to make it activate prior to other types, False after
     */
    public static void register(RailType type, boolean withPriority) {
        ArrayList<RailType> newValues = new ArrayList<RailType>(values);
        if (withPriority) {
            newValues.add(0, type);
        } else {
            newValues.add(type);
        }
        values = newValues;
        type._registered = true;
        RailLookup.forceRecalculation();
    }

    /**
     * Gets a Collection of all available Rail Types.
     * The NONE constant is not included.
     *
     * @return Rail Types
     */
    public static Collection<RailType> values() {
        return values;
    }

    /**
     * Tries to find the Rail Type a specific rails block represents.
     * If none is identified, NONE is returned.
     *
     * @param railsBlock to get the RailType of
     * @return the RailType, or NONE if not found
     */
    public static RailType getType(Block railsBlock) {
        if (railsBlock != null) {
            return getType(railsBlock, WorldUtil.getBlockData(railsBlock));
        }
        return NONE;
    }

    /**
     * Tries to find the Rail Type a specific rails block represents.
     * If none is identified, NONE is returned.<br>
     * <br>
     * Null input arguments are not allowed.
     *
     * @param railsBlock to get the RailType of
     * @param railsBlockData BlockData of railsBlock, avoids extra lookup
     * @return the RailType, or NONE if not found
     */
    public static RailType getType(Block railsBlock, BlockData railsBlockData) {
        for (RailType type : values()) {
            if (checkRailTypeIsAt(type, railsBlock, railsBlockData)) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * Helper method to check whether a given RailType matches the provided block and block
     * data. Will choose the most optimized method, and handle errors in custom implementations
     * sanely.
     *
     * @param type Rail type
     * @param railsBlock Rail block
     * @param railsBlockData Block data of Rail block
     * @return True if the rail type is at the block
     */
    public static boolean checkRailTypeIsAt(RailType type, Block railsBlock, BlockData railsBlockData) {
        try {
            return type.isComplexRailBlock() ? type.isRail(railsBlock) : type.isRail(railsBlockData);
        } catch (Throwable t) {
            handleCriticalError(type, t);
            return false;
        }
    }

    /**
     * Checks all registered rail types and attempts to load it into a {@link RailState} object. This provides
     * information such as rails block and rail type used. Some performance enhancements are used to make this
     * lookup faster for repeated calls for positions inside the same block. Note that the position does not
     * have to be the same position as the rails block itself. For example, rails that have trains hover above
     * or below it will have entirely different rails blocks.
     * 
     * @param state RailState to load with rail information
     * @return True if rails were found (railtype != NONE), False otherwise
     */
    public static boolean loadRailInformation(RailState state) {
        state.initEnterDirection();
        state.position().assertAbsolute();

        final RailPiece[] cachedPieces = state.railLookup().findAtStatePosition(state);
        if (cachedPieces.length == 0) {
            state.setRailPiece(RailPiece.create(RailType.NONE,
                    state.positionBlock(), state.railLookup()));
            return false;
        }

        // If more than one rail piece exists here, pick the most appropriate one for this position
        // This is a little bit slower, but required for rare instances of multiple rails per block
        RailPiece resultPiece = cachedPieces[0];
        if (cachedPieces.length >= 2) {
            RailPath.ProximityInfo nearest = null;
            for (RailPiece piece : cachedPieces) {
                state.setRailPiece(piece);
                RailLogic logic = state.loadRailLogic();
                RailPath path = logic.getPath();
                RailPath.ProximityInfo near = path.getProximityInfo(state.railPosition(), state.motionVector());
                if (nearest == null || near.compareTo(nearest) < 0) {
                    nearest = near;
                    resultPiece = piece;
                }
            }
        }

        state.setRailPiece(resultPiece);
        return true;
    }

    /**
     * Looks up a rail piece used at a block position
     *
     * @param blockPosition block position where the Minecart is at
     * @return rail piece at this block, null if no rails are found
     * @deprecated Use {@link #findRailPiece(Location)} instead
     */
    @Deprecated
    public static RailPiece findRailPiece(Block blockPosition) {
        RailState state = new RailState();
        state.position().setLocationMidOf(blockPosition);
        state.setRailPiece(RailPiece.createWorldPlaceholder(blockPosition.getWorld()));
        if (loadRailInformation(state)) {
            return state.railPiece();
        } else {
            return null;
        }
    }

    /**
     * Checks all registered rail types and attempts to find the rail piece used for the Minecart position specified.
     * Some performance enhancements are used to make this lookup faster for repeated calls for positions inside
     * the same block. Note that the position does not have to be the same position as the rails block
     * itself. For example, rails that have trains hover above or below it will have entirely different
     * rails blocks.
     * 
     * @param position in world coordinates where to look for rails
     * @return rail piece at this position, null if no rails are found
     */
    public static RailPiece findRailPiece(Location position) {
        RailState state = new RailState();
        state.position().setLocation(position);
        state.setRailPiece(RailPiece.createWorldPlaceholder(position.getWorld()));
        if (loadRailInformation(state)) {
            return state.railPiece();
        } else {
            return null;
        }
    }

    public RailType() {
        // Detect whether isRail(world, x, y, z) is overrided
        // If it is not, we can optimize lookup for this rail type
        this._isComplexRailBlock = CommonUtil.isMethodOverrided(RailType.class, getClass(),
                "isRail", World.class, int.class, int.class, int.class);
        // Detect whether onBlockPhysics and/or isRailsSupported are overrided
        // If so, block physics need to be handled, for which a potentially expensive
        // isRail() check needs to be executed.
        this._isHandlingPhysics = CommonUtil.isMethodOverrided(RailType.class, getClass(),
                "onBlockPhysics", org.bukkit.event.block.BlockPhysicsEvent.class) ||
                                  CommonUtil.isMethodOverrided(RailType.class, getClass(),
                "isRailsSupported", Block.class);
    }

    /**
     * Checks whether the block data given denote this type of Rail.
     * This function is called from {@link #isRail(world, x, y, z)} exclusively.
     * If the rails is more complex than one type of Block, override that method
     * and ignore this one.
     *
     * @param blockData of the Block
     * @return True if it is this type of Rail, False if not
     */
    public abstract boolean isRail(BlockData blockData);

    /**
     * Checks whether the Block specified denote this type of Rail.
     * To check for complex structures, this method should be overrided to check for that.
     *
     * @param world the Block is in
     * @param x     - coordinate of the Block
     * @param y     - coordinate of the Block
     * @param z     - coordinate of the Block
     * @return True if it is this Rail, False if not
     */
    public boolean isRail(World world, int x, int y, int z) {
        return isRail(WorldUtil.getBlockData(world, x, y, z));
    }

    /**
     * Checks whether the Block face specified denote this type of Rail
     *
     * @param block  to check
     * @param offset face from the block to check
     * @return True if it is this Rail, False if not
     */
    public final boolean isRail(Block block, BlockFace offset) {
        return isRail(block.getWorld(), block.getX() + offset.getModX(), block.getY() + offset.getModY(),
                block.getZ() + offset.getModZ());
    }

    /**
     * Checks whether the Block specified denote this type of Rail
     *
     * @param block to check
     * @return True if it is this Rail, False if not
     */
    public final boolean isRail(Block block) {
        return isRail(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * Gets whether this RailType is still registered. If this rail type was never
     * registered using {@link #register(RailType, boolean)}, or was last unregistered
     * using {@link #unregister(RailType)}, then this method will return false.
     *
     * @return True if this RailType is still registered, and can be used
     */
    public final boolean isRegistered() {
        return this._registered;
    }

    /**
     * Gets the bounding box of a rails block. This bounding box is used
     * to calculate the face direction when a minecart enters the rails.
     * It should surround the entire rails section for optimal results.
     * By default returns a standard 1x1x1 bounding box.
     * 
     * @param state of the rails
     * @return bounding box
     */
    public RailAABB getBoundingBox(RailState state) {
        return RailAABB.BLOCK;
    }

    /**
     * Gets whether {@link #isRail(World, x, y, z)} is overrided, indicating this rail
     * type is more complex than a single block
     * 
     * @return True if this is a complex rail block
     */
    public final boolean isComplexRailBlock() {
        return this._isComplexRailBlock;
    }

    /**
     * Gets whether {@link #onBlockPhysics(BlockPhysicsEvent)} or
     * {@link #isRailsSupported(Block)} are overrided, indicating this rail type
     * will need to be notified of physics events.
     *
     * @return True if physics are handled by this rail type
     */
    public final boolean isHandlingPhysics() {
        return this._isHandlingPhysics;
    }

    /**
     * Gets whether blocks surrounding the rails block indicate the rails is used upside-down.
     * It is only upside-down when the block 'below' the rails is air, and a solid block exists above.
     * This strict rule avoids regular tracks turning into upside-down tracks.
     * Rail types that don't support upside-down Minecarts should always return false here.
     * 
     * @param railsBlock
     * @return True if the rails are upside-down
     */
    public boolean isUpsideDown(Block railsBlock) {
        return false;
    }

    /**
     * Finds the rail block at a block position
     *
     * @param pos Block a Minecart is 'at'
     * @return the rail of this type, or null if not found
     * @deprecated Use {@link #findRails(Block)} instead.
     */
    @Deprecated
    public Block findRail(Block pos) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Tries to find all the Rails blocks of this Rail Type for a Minecart whose position is inside
     * a particular Block. Multiple different rails can have logic for a single block area. If no rails
     * are found, it is recommended to return {@link Collections#emptyList()}.
     * 
     * @param positionBlock to find rails at
     * @return railsBlocks list of rails blocks of this rail type (do NOT return null!)
     */
    public List<Block> findRails(Block positionBlock) {
        Block rail = this.findRail(positionBlock);
        return (rail == null) ? Collections.emptyList() : Collections.singletonList(rail);
    }

    /**
     * Finds the minecart pos of a given rail block
     *
     * @param trackBlock where this Rail Type is at
     * @return Minecart position
     * @deprecated This is no longer being used except for the also-deprecated TrackIterator
     */
    @Deprecated
    public Block findMinecartPos(Block trackBlock) {
        return trackBlock;
    }

    /**
     * Gets an array containing all possible directions a Minecart can move on the trackBlock.
     *
     * @param trackBlock to use
     * @return all possible directions the Minecart can move
     * @deprecated Call / implement {@link #getJunctions(Block)} instead (if needed)
     */
    @Deprecated
    public abstract BlockFace[] getPossibleDirections(Block trackBlock);

    /**
     * Gets an array containing all possible junctions that can be taken for a particular rail block.
     * There does not have to be a valid rail at the end for a junction to exist. By default the two end
     * points of the path returned by the logic for a 'down' direction are returned.
     * 
     * @param railBlock where this Rail Type is at
     * @return list of junctions supported by this rail type, empty if no junctions are available
     */
    public List<RailJunction> getJunctions(Block railBlock) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.create(this, railBlock));
        state.position().setLocation(this.getSpawnLocation(railBlock, BlockFace.DOWN));
        state.position().setMotion(BlockFace.DOWN);
        state.initEnterDirection();

        RailPath path = this.getLogic(state).getPath();
        if (path.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(new RailJunction("1", path.getStartPosition()),
                                 new RailJunction("2", path.getEndPosition()));
        }
    }

    /**
     * Prepares a {@link RailState} when taking a junction returned by {@link #getJunctions(Block)}.
     * Feeding this state into a walking point will enable further discovery past the junction.
     * 
     * @param railBlock where this Rail Type is at
     * @param junction to check
     * @return RailState after taking the junction, null if there is no rails here
     */
    public RailState takeJunction(Block railBlock, RailJunction junction) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.create(this, railBlock));
        junction.position().copyTo(state.position());
        state.position().makeAbsolute(railBlock);
        state.position().smallAdvance();
        if (!loadRailInformation(state)) {
            return null; // No rail here
        }
        if (state.railType() == this && state.railBlock().equals(railBlock)) {
            return null; // Same rail - avoid cyclical loop error
        }
        return state;
    }

    /**
     * Switches the rails from one junction to another. Junctions are used from {@link #getJunctions(railBlock)}.<br>
     * <br>
     * This should be implemented by RailType implementations to switch junctions. It should not be called,
     * for that use {@link RailPiece#switchJunction(RailJunction, RailJunction)} instead.
     * 
     * @param railBlock where this Rail Type is at
     * @param from junction
     * @param to junction
     */
    public void switchJunction(Block railBlock, RailJunction from, RailJunction to) {
    }

    /**
     * Obtains the direction of this type of Rails.
     * This is the direction along minecarts move.
     *
     * @param railsBlock to get it for
     * @return rails Direction
     * @deprecated BlockFace offers too little information, use RailState for computing this instead
     */
    @Deprecated
    public BlockFace getDirection(Block railsBlock) {
        RailState state = new RailState();
        state.setRailPiece(RailPiece.create(this, railsBlock));
        state.setPosition(Position.fromLocation(this.getSpawnLocation(railsBlock, BlockFace.SELF)));
        state.initEnterDirection();
        return state.enterFace();
    }

    /**
     * Gets the track-relative direction to look for signs related to this Rails
     * 
     * @param railsBlock to find the sign column direction for
     * @return direction to look for signs relating to this rails block
     */
    public abstract BlockFace getSignColumnDirection(Block railsBlock);

    /**
     * Gets the default trigger (movement) directions of trains on the rails
     * that can activate signs. These directions can be overrided on the sign, so these
     * are the default if none are specified.<br>
     * <br>
     * By default returns BLOCK_SIDES (up/down/north/east/south/west) to indicate all
     * possible directions activate the sign.
     * 
     * @param railBlock The rail block that has this RailType
     * @param signBlock The sign block of the sign being activated
     * @param signFacing The facing of the sign being activated
     * @return sign trigger directions
     */
    public BlockFace[] getSignTriggerDirections(Block railBlock, Block signBlock, BlockFace signFacing) {
        return FaceUtil.BLOCK_SIDES;
    }

    /**
     * Gets the first block of the sign column where signs for this rail are located.
     * 
     * @param railsBlock
     * @return sign column start
     */
    public Block getSignColumnStart(Block railsBlock) {
        return railsBlock;
    }

    /**
     * Discovers the {@link TrackedSign} signs activated by trains when they drive over this RailType
     * on a particular rail block. The input railPiece should be used when initializing the TrackedSign,
     * and the input signController can be used to efficiently look up real sign blocks.<br>
     * <br>
     * It is allowed to return a custom TrackedSign implementation here that represents a fake sign.<Br>
     * <br>
     * By default uses {@link #getSignColumnDirection(Block)} and {@link #getSignColumnStart(Block)} to
     * search for real sign blocks.
     *
     * @param railPiece Rail Piece with the same RailType as this one being queried
     * @param signController Sign Controller which can be queried for real sign blocks near the rails
     * @param result List of tracked signs to write the found signs to
     */
    public void discoverSigns(RailPiece railPiece, SignControllerWorld signController, List<TrackedSign> result) {
        Block columnStart = getSignColumnStart(railPiece.block());
        if (columnStart == null) {
            return;
        }

        BlockFace direction = getSignColumnDirection(railPiece.block());
        if (direction == null || direction == BlockFace.SELF) {
            return;
        }

        signController.forEachSignInColumn(columnStart, direction, true, tracker -> {
            result.add(TrackedSign.forRealSign(tracker, true, railPiece));
            if (CommonCapabilities.HAS_SIGN_BACK_TEXT) {
                result.add(TrackedSign.forRealSign(tracker, false, railPiece));
            }
        });
    }

    /**
     * Obtains the Rail Logic to use for the Minecart at the (previously calculated) rail position in a World.
     *
     * @param member to get the logic for (can be null when used by track walkers for e.g. spawning)
     * @param railsBlock the Minecart is driving on
     * @param direction in which the Minecart is moving. Only block directions (north/east/south/west/up/down) are used.
     * @return Rail Logic
     * @deprecated Use {@link #getLogic(RailState)} instead.
     */
    @Deprecated
    public RailLogic getLogic(MinecartMember<?> member, Block railsBlock, BlockFace direction) {
        return RailLogicAir.INSTANCE;
    }

    /**
     * Obtains the Rail Logic to use for the rail state situation specified
     * 
     * @param state input
     * @return desired rail logic
     */
    public RailLogic getLogic(RailState state) {
        return getLogic(state.member(), state.railBlock(), state.enterFace());
    }

    /**
     * Called one tick after a block of this Rail Type was placed down in the world
     * 
     * @param railsBlock that was placed
     */
    public void onBlockPlaced(Block railsBlock) {
    }

    /**
     * Called when block physics are being performed for a Block matching this Rail Type.
     * 
     * @param event block physics event
     */
    public void onBlockPhysics(BlockPhysicsEvent event) {
    }

    /**
     * Gets whether this Rails Type is supported by a block it is attached to.
     * If this returns False, the rails block is automatically broken and an item is dropped.
     * 
     * @param railsBlock to check
     * @return True if the rails block is supported
     */
    public boolean isRailsSupported(Block railsBlock) {
        return true;
    }

    /**
     * Called right before a Minecart is moved from one point to the other.
     * This is called after the pre-movement updates performed by rail logic.
     *
     * @param member that is about to be moved
     */
    public void onPreMove(MinecartMember<?> member) {
    }

    /**
     * Called right after a Minecart was moved from one point to the other.
     * This is called after the post-movement updates performed by rail logic.
     *
     * @param member that just moved
     */
    public void onPostMove(MinecartMember<?> member) {
    }

    /**
     * Handles collision with this Rail Type
     *
     * @param with    Minecart that his this Rail
     * @param block   of this Rail
     * @param hitFace of this Rail
     * @return True if collision is allowed, False if not
     */
    public boolean onCollide(MinecartMember<?> with, Block block, BlockFace hitFace) {
        return true;
    }

    /**
     * Gets whether this Rail Type uses block activation as part of basic physics.
     * Examples are detector rails and pressure plates.
     * This property is used to optimize movement physics by not checking when not needed.
     * Is ignored completely when this optimization is turned off in the configuration.
     * 
     * @param railBlock
     * @return True if block activation is used
     */
    public boolean hasBlockActivation(Block railBlock) {
        return false;
    }

    /**
     * Handles a Minecart colliding with a Block while using this Rail Type.
     *
     * @param member     that collided
     * @param railsBlock the member is driving on
     * @param hitBlock   the Minecart hit
     * @param hitFace    the Minecart hit
     * @return True if collision is allowed, False if not
     */
    public boolean onBlockCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock, BlockFace hitFace) {
        return true;
    }

    /**
     * Gets whether a minecart hit a block 'head-on', meaning it should stop the train
     * 
     * @param member that hit a block
     * @param railsBlock the minecart is on
     * @param hitBlock that was hit
     * @return True if head-on, False if not
     */
    public boolean isHeadOnCollision(MinecartMember<?> member, Block railsBlock, Block hitBlock) {
        return false;
    }

    /**
     * Gets the initial location of a minecart when placed on this Rail Type.
     * By default spawns the Minecart on top of the block, facing the orientation
     * 
     * @param railsBlock to spawn on
     * @param orientation horizontal orientation of the one that placed the minecart
     * @return spawn location
     */
    public abstract Location getSpawnLocation(Block railsBlock, BlockFace orientation);

    /**
     * Gets rails texture information about this Rail Type for a particular Block.
     * This texture is displayed in the editor.
     * 
     * @param railsBlock
     * @return rails texture
     */
    public RailsTexture getRailsTexture(Block railsBlock) {
        return new RailsTexture();
    }

}
