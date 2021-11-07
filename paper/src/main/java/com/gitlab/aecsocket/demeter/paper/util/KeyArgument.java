package com.gitlab.aecsocket.demeter.paper.util;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.Caption;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.exceptions.parsing.ParserException;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * Command argument which parses a {@link Key}.
 * @param <C> The command sender type.
 */
public final class KeyArgument<C> extends CommandArgument<C, Key> {
    /** When a component ID cannot be parsed. */
    public static final Caption ARGUMENT_PARSE_FAILURE_KEY= Caption.of("argument.parse.failure.key");

    private KeyArgument(
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<@NonNull CommandContext<C>,
                    @NonNull String, @NonNull List<@NonNull String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription
    ) {
        super(required, name, new ComponentParser<>(), defaultValue, Key.class, suggestionsProvider, defaultDescription);
    }

    /**
     * Create a new builder
     *
     * @param name   Name of the component
     * @param <C>    Command sender type
     * @return Created builder
     */
    public static <C> @NonNull Builder<C> newBuilder(final @NonNull String name) {
        return new Builder<>(name);
    }

    /**
     * Create a new required command component
     *
     * @param name   Component name
     * @param <C>    Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Key> of(final @NonNull String name) {
        return KeyArgument.<C>newBuilder(name).asRequired().build();
    }

    /**
     * Create a new optional command component
     *
     * @param name   Component name
     * @param <C>    Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Key> optional(final @NonNull String name) {
        return KeyArgument.<C>newBuilder(name).asOptional().build();
    }

    /**
     * Create a new required command component with a default value
     *
     * @param name         Component name
     * @param defaultValue Default value
     * @param <C>          Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Key> optional(
            final @NonNull String name,
            final @NonNull Key defaultValue
    ) {
        return KeyArgument.<C>newBuilder(name).asOptionalWithDefault(defaultValue.toString()).build();
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, Key> {
        private Builder(final @NonNull String name) {
            super(Key.class, name);
        }

        /**
         * Builder a new example component
         *
         * @return Constructed component
         */
        @Override
        public @NonNull KeyArgument<C> build() {
            return new KeyArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

    public static final class ComponentParser<C> implements ArgumentParser<C, Key> {
        @Override
        public @NonNull ArgumentParseResult<Key> parse(
                final @NonNull CommandContext<C> ctx,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            //noinspection PatternValidation
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NoInputProvidedException(
                        Key.class,
                        ctx
                ));
            }
            inputQueue.remove();

            try {
                //noinspection PatternValidation
                return ArgumentParseResult.success(Key.key(input));
            } catch (InvalidKeyException e) {
                return ArgumentParseResult.failure(new ParseException(input, ctx, e));
            }
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(@NonNull CommandContext<C> ctx, @NonNull String input) {
            return Collections.emptyList();
        }
    }

    public static final class ParseException extends ParserException {
        public ParseException(String input, CommandContext<?> ctx, Exception e) {
            super(Key.class, ctx, ARGUMENT_PARSE_FAILURE_KEY,
                    CaptionVariable.of("input", input),
                    CaptionVariable.of("error", e.getMessage()));
        }
    }
}
