package com.apophisgames.rustyraiding.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class RaidingConfig {

    public static final BuilderCodec<RaidingConfig> CODEC = BuilderCodec.builder(RaidingConfig.class, RaidingConfig::new)
            .append(new KeyedCodec<Integer>("Width", Codec.INTEGER),
                    (findConfig, integer, extraInfo) -> findConfig.Width = integer,
                    (findConfig, extraInfo) -> findConfig.Width).add()

            .append(new KeyedCodec<Integer>("Height", Codec.INTEGER),
                    (findConfig, integer, extraInfo) -> findConfig.Height = integer,
                    (findConfig, extraInfo) -> findConfig.Height).add()

            .append(new KeyedCodec<Integer>("ReinforceBlockAmount", Codec.INTEGER),
                    (findConfig, integer, extraInfo) -> findConfig.ReinforceBlockAmount = integer,
                    (findConfig, extraInfo) -> findConfig.ReinforceBlockAmount).add()

            .append(new KeyedCodec<Boolean>("ProtectSoftBlocks", Codec.BOOLEAN),
                    (findConfig, bool, extraInfo) -> findConfig.ProtectSoftBlocks = bool,
                    (findConfig, extraInfo) -> findConfig.ProtectSoftBlocks).add()

            .append(new KeyedCodec<Boolean>("ProtectBypassTypeBlocks", Codec.BOOLEAN),
                    (findConfig, bool, extraInfo) -> findConfig.ProtectBypassTypeBlocks = bool,
                    (findConfig, extraInfo) -> findConfig.ProtectBypassTypeBlocks).add()
            .build();

    private int Height = 15;
    private int Width = 15;
    private int ReinforceBlockAmount = 50;
    private boolean ProtectSoftBlocks = false;
    private boolean ProtectBypassTypeBlocks = false;


    public RaidingConfig() {

    }

    public int getWidth() {
        return Width;
    }
    public int getHeight() {
        return Height;
    }

    public int getReinforceBlockAmount() {
        return ReinforceBlockAmount;
    }

    public boolean getProtectSoftBlocks() {
        return ProtectSoftBlocks;
    }

    public boolean getProtectBypassTypeBlocks(){
        return ProtectBypassTypeBlocks;
    }
}