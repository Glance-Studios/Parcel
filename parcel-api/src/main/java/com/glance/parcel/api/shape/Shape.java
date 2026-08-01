package com.glance.parcel.api.shape;

import com.glance.parcel.api.math.BlockBox;
import org.jetbrains.annotations.NotNull;

/**
 * A block-aligned volume.
 *
 * <p>Sealed so the mesher and the serialiser can dispatch exhaustively. If third-party shape types
 * are ever needed this becomes an open interface plus a registry - deliberately deferred until
 * something actually asks for it.
 */
public sealed interface Shape permits Cuboid, Prism {

    /**
     * @return whether this shape covers the given block
     */
    boolean contains(int x, int y, int z);

    /**
     * @return the tight axis-aligned bounds of this shape
     */
    @NotNull
    BlockBox bounds();

    /**
     * @return a stable identifier used for serialisation
     */
    @NotNull
    String typeId();
}
