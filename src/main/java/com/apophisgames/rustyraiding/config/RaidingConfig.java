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
            .build();

    private int Height = 15;
    private int Width = 15;
    private int ReinforceBlockAmount = 10;


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
}