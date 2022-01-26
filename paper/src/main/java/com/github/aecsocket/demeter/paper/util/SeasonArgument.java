package com.github.aecsocket.demeter.paper.util;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.Caption;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.exceptions.parsing.ParserException;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;

import com.github.aecsocket.demeter.paper.DemeterPlugin;
import com.github.aecsocket.demeter.paper.feature.Seasons;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * Command argument which parses a {@link Seasons.Season}.
 * @param <C> The command sender type.
 */
public final class SeasonArgument<C> extends CommandArgument<C, Seasons.Season> {
    /** When a season cannot be parsed. */
    public static final Caption ARGUMENT_PARSE_FAILURE_SEASON = Caption.of("argument.parse.failure.season");

    private final DemeterPlugin plugin;

    private SeasonArgument(
            final DemeterPlugin plugin,
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<@NonNull CommandContext<C>,
                    @NonNull String, @NonNull List<@NonNull String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription
    ) {
        super(required, name, new SeasonParser<>(plugin), defaultValue, Seasons.Season.class, suggestionsProvider, defaultDescription);
        this.plugin = plugin;
    }

    /**
     * Create a new builder
     *
     * @param plugin Plugin
     * @param name   Name of the component
     * @param <C>    Command sender type
     * @return Created builder
     */
    public static <C> @NonNull Builder<C> newBuilder(final DemeterPlugin plugin, final @NonNull String name) {
        return new Builder<>(plugin, name);
    }

    /**
     * Create a new required command component
     *
     * @param plugin Plugin
     * @param name   Component name
     * @param <C>    Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Seasons.Season> of(final DemeterPlugin plugin, final @NonNull String name) {
        return SeasonArgument.<C>newBuilder(plugin, name).asRequired().build();
    }

    /**
     * Create a new optional command component
     *
     * @param plugin Plugin
     * @param name   Component name
     * @param <C>    Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Seasons.Season> optional(final DemeterPlugin plugin, final @NonNull String name) {
        return SeasonArgument.<C>newBuilder(plugin, name).asOptional().build();
    }

    /**
     * Create a new required command component with a default value
     *
     * @param plugin       Plugin
     * @param name         Component name
     * @param defaultValue Default value
     * @param <C>          Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Seasons.Season> optional(
            final DemeterPlugin plugin,
            final @NonNull String name,
            final @NonNull Key defaultValue
    ) {
        return SeasonArgument.<C>newBuilder(plugin, name).asOptionalWithDefault(defaultValue.toString()).build();
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, Seasons.Season> {
        private final DemeterPlugin plugin;

        private Builder(final DemeterPlugin plugin, final @NonNull String name) {
            super(Seasons.Season.class, name);
            this.plugin = plugin;
        }

        /**
         * Builder a new example component
         *
         * @return Constructed component
         */
        @Override
        public @NonNull SeasonArgument<C> build() {
            return new SeasonArgument<>(
                    plugin,
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

    public static final class SeasonParser<C> implements ArgumentParser<C, Seasons.Season> {
        private final DemeterPlugin plugin;

        public SeasonParser(DemeterPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NonNull ArgumentParseResult<Seasons.Season> parse(
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

            Seasons.Season season = plugin.seasons().config().seasons.get(input);
            if (season == null)
                return ArgumentParseResult.failure(new ParseException(input, ctx));
            return ArgumentParseResult.success(season);
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(@NonNull CommandContext<C> ctx, @NonNull String input) {
            return new ArrayList<>(plugin.seasons().config().seasons.keySet());
        }
    }

    public static final class ParseException extends ParserException {
        public ParseException(String input, CommandContext<?> ctx) {
            super(Seasons.Season.class, ctx, ARGUMENT_PARSE_FAILURE_SEASON,
                    CaptionVariable.of("input", input));
        }
    }
}
