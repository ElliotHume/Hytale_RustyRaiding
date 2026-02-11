package com.apophisgames.rustyraiding;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ToolCupboardDataComponent implements Component<ChunkStore> {

    public static final BuilderCodec CODEC = BuilderCodec.builder(ToolCupboardDataComponent.class, () -> new ToolCupboardDataComponent(UUID.randomUUID().toString()))
            .append(new KeyedCodec<String>("UUID", Codec.STRING), ToolCupboardDataComponent::setUuid, ToolCupboardDataComponent::getUuid).add()
            .build();

    @Nonnull
    private String uuid;

    public ToolCupboardDataComponent(String uuid) {
        this.uuid = uuid;
    }

    @Nonnull
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @NullableDecl
    @Override
    public Component<ChunkStore> clone() {
        return new ToolCupboardDataComponent(uuid);
    }

}
