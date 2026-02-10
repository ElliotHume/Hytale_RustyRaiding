package com.apophisgames.rustyraiding.util;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Fluent builder for creating styled messages.
 */
public final class MessageBuilder {

    private Message message;

    private MessageBuilder(@Nonnull String text) {
        this.message = Message.raw(text);
    }

    private MessageBuilder(@Nonnull Message message) {
        this.message = message;
    }

    @Nonnull
    public static MessageBuilder create(@Nonnull String text) {
        return new MessageBuilder(Objects.requireNonNull(text));
    }

    @Nonnull
    public static MessageBuilder create() {
        return new MessageBuilder("");
    }

    @Nonnull
    public static MessageBuilder success(@Nonnull String text) {
        return create(text).color(ColorPalette.SUCCESS);
    }

    @Nonnull
    public static MessageBuilder error(@Nonnull String text) {
        return create(text).color(ColorPalette.ERROR);
    }

    @Nonnull
    public static MessageBuilder warning(@Nonnull String text) {
        return create(text).color(ColorPalette.WARNING);
    }

    @Nonnull
    public static MessageBuilder info(@Nonnull String text) {
        return create(text).color(ColorPalette.INFO);
    }

    @Nonnull
    public static MessageBuilder muted(@Nonnull String text) {
        return create(text).color(ColorPalette.MUTED);
    }

    @Nonnull
    public MessageBuilder color(@Nonnull String hexColor) {
        this.message = this.message.color(hexColor);
        return this;
    }

    @Nonnull
    public MessageBuilder bold() {
        this.message = this.message.bold(true);
        return this;
    }

    @Nonnull
    public MessageBuilder append(@Nonnull String text) {
        this.message = this.message.insert(Message.raw(text));
        return this;
    }

    @Nonnull
    public MessageBuilder append(@Nonnull String text, @Nonnull String hexColor) {
        this.message = this.message.insert(Message.raw(text).color(hexColor));
        return this;
    }

    @Nonnull
    public MessageBuilder append(@Nonnull String text, @Nonnull String hexColor, boolean bold) {
        Message part = Message.raw(text).color(hexColor);
        if (bold) {
            part = part.bold(true);
        }
        this.message = this.message.insert(part);
        return this;
    }

    @Nonnull
    public Message build() {
        Message prefix = Message.raw("[RustyRaiding] ").color(ColorPalette.ERROR);
        return prefix.insert(message);
    }
}
