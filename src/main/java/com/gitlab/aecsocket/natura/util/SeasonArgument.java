package com.gitlab.aecsocket.natura.util;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.Caption;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.exceptions.parsing.ParserException;
import com.gitlab.aecsocket.natura.NaturaPlugin;
import com.gitlab.aecsocket.natura.feature.Seasons;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

public class SeasonArgument<C> extends CommandArgument<C, Seasons.Season> {
    public static final Caption ARGUMENT_PARSE_FAILURE_SEASON = Caption.of("argument.parse.failure.season");

    public static final class Exception extends ParserException {
        public Exception(String input, CommandContext<?> ctx) {
            super(SeasonArgument.class, ctx, ARGUMENT_PARSE_FAILURE_SEASON, CaptionVariable.of("input", input));
        }
    }

    private SeasonArgument(
            final NaturaPlugin plugin,
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<@NonNull CommandContext<C>,
                    @NonNull String, @NonNull List<@NonNull String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription
    ) {
        super(required, name, new SeasonParser<>(plugin), defaultValue, Seasons.Season.class, suggestionsProvider, defaultDescription);
    }

    public static <C> @NonNull Builder<C> newBuilder(final @NonNull String name, final NaturaPlugin plugin) {
        return new Builder<>(name, plugin);
    }

    public static <C> @NonNull CommandArgument<C, Seasons.Season> of(final @NonNull String name, final NaturaPlugin plugin) {
        return SeasonArgument.<C>newBuilder(name, plugin).asRequired().build();
    }

    public static <C> @NonNull CommandArgument<C, Seasons.Season> optional(final @NonNull String name, final NaturaPlugin plugin) {
        return SeasonArgument.<C>newBuilder(name, plugin).asOptional().build();
    }

    public static <C> @NonNull CommandArgument<C, Seasons.Season> optional(
            final @NonNull String name,
            final Seasons.@NonNull Season defaultValue,
            final NaturaPlugin plugin
    ) {
        return SeasonArgument.<C>newBuilder(name, plugin).asOptionalWithDefault(defaultValue.toString()).build();
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, Seasons.Season> {
        private final NaturaPlugin plugin;

        private Builder(final @NonNull String name, final NaturaPlugin plugin) {
            super(Seasons.Season.class, name);
            this.plugin = plugin;
        }

        public NaturaPlugin plugin() { return plugin; }

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
        private final NaturaPlugin plugin;

        public SeasonParser(NaturaPlugin plugin) {
            this.plugin = plugin;
        }

        public NaturaPlugin plugin() { return plugin; }

        @Override
        public @NonNull ArgumentParseResult<Seasons.@NonNull Season> parse(@NonNull CommandContext<@NonNull C> ctx, @NonNull Queue<@NonNull String> input) {
            String arg = input.peek();
            if (arg == null)
                return ArgumentParseResult.failure(new NoInputProvidedException(SeasonArgument.class, ctx));

            Seasons.Season season = plugin.seasons().config().seasons.get(arg);
            if (season == null)
                return ArgumentParseResult.failure(new Exception(arg, ctx));
            input.remove();
            return ArgumentParseResult.success(season);
        }
    }
}
