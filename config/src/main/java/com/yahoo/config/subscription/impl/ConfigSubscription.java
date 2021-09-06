// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.DirSource;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.config.subscription.JarSource;
import com.yahoo.config.subscription.RawSource;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.DefContent;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Represents one active subscription to one config
 *
 * @author Vegard Havdal
 */
public abstract class ConfigSubscription<T extends ConfigInstance> {

    protected static final Logger log = Logger.getLogger(ConfigSubscription.class.getName());
    protected final ConfigSubscriber subscriber;
    private final AtomicReference<ConfigState<T>> config = new AtomicReference<>();
    protected final ConfigKey<T> key;
    protected final Class<T> configClass;
    private volatile RuntimeException exception = null;
    private State state = State.OPEN;

    public static class ConfigState<T extends ConfigInstance> {

        private final boolean configChanged;
        private final boolean generationChanged;
        private final T config;
        private final Long generation;
        private final boolean applyOnRestart;
        private final PayloadChecksum payloadChecksum;

        private ConfigState(boolean generationChanged,
                            Long generation,
                            boolean applyOnRestart,
                            boolean configChanged,
                            T config,
                            PayloadChecksum payloadChecksum) {
            this.generationChanged = generationChanged;
            this.generation = generation;
            this.applyOnRestart = applyOnRestart;
            this.configChanged = configChanged;
            this.config = config;
            this.payloadChecksum = payloadChecksum;
        }

        private ConfigState(Long generation, T config, PayloadChecksum payloadChecksum) {
            this(false, generation, false, false, config, payloadChecksum);
        }

        private ConfigState() {
            this(false, 0L, false, false, null, PayloadChecksum.empty());
        }

        private ConfigState<T> createUnchanged() {  return new ConfigState<>(generation, config, payloadChecksum); }

        public boolean isConfigChanged() { return configChanged; }

        public boolean isGenerationChanged() { return generationChanged; }

        public Long getGeneration() { return generation; }

        public boolean applyOnRestart() { return applyOnRestart; }

        public T getConfig() { return config; }

        public PayloadChecksum getChecksum() { return payloadChecksum; }

    }

    /**
     * If non-null: The user has set this generation explicitly. nextConfig should take this into account.
     * Access to these variables _must_ be synchronized, as nextConfig and reload() is likely to be run from
     * independent threads.
     */
    private final AtomicReference<Long> reloadedGeneration = new AtomicReference<>();

    enum State {
        OPEN, CLOSED
    }

    /**
     * Initializes one subscription
     *
     * @param key        a {@link ConfigKey}
     * @param subscriber the subscriber for this subscription
     */
    ConfigSubscription(ConfigKey<T> key, ConfigSubscriber subscriber) {
        this.key = key;
        this.configClass = key.getConfigClass();
        this.subscriber = subscriber;
        this.config.set(new ConfigState<>());
    }

    /**
     * Correct type of ConfigSubscription instance based on type of source or form of config id
     *
     * @param key        a {@link ConfigKey}
     * @param subscriber the subscriber for this subscription
     * @return a subclass of a ConfigsSubscription
     */
    public static <T extends ConfigInstance> ConfigSubscription<T> get(ConfigKey<T> key, ConfigSubscriber subscriber,
                                                                       ConfigSource source, TimingValues timingValues) {
        String configId = key.getConfigId();
        if (source instanceof RawSource || configId.startsWith("raw:")) return getRawSub(key, subscriber, source);
        if (source instanceof FileSource || configId.startsWith("file:")) return getFileSub(key, subscriber, source);
        if (source instanceof DirSource || configId.startsWith("dir:")) return getDirFileSub(key, subscriber, source);
        if (source instanceof JarSource || configId.startsWith("jar:")) return getJarSub(key, subscriber, source);
        if (source instanceof ConfigSet) return new ConfigSetSubscription<>(key, subscriber, source);
        if (source instanceof ConfigSourceSet) return new JRTConfigSubscription<>(key, subscriber, source, timingValues);
        throw new IllegalArgumentException("Unknown source type: " + source);
    }

    private static <T extends ConfigInstance> JarConfigSubscription<T> getJarSub(
            ConfigKey<T> key, ConfigSubscriber subscriber, ConfigSource source) {
        String jarName;
        String path = "config/";
        if (source instanceof JarSource) {
            JarSource js = (JarSource) source;
            jarName = js.getJarFile().getName();
            if (js.getPath() != null) path = js.getPath();
        } else {
            jarName = key.getConfigId().replace("jar:", "").replaceFirst("\\!/.*", "");
            if (key.getConfigId().contains("!/")) path = key.getConfigId().replaceFirst(".*\\!/", "");
        }
        return new JarConfigSubscription<>(key, subscriber, jarName, path);
    }

    private static <T extends ConfigInstance> ConfigSubscription<T> getFileSub(
            ConfigKey<T> key, ConfigSubscriber subscriber, ConfigSource source) {
        File file = ((source instanceof FileSource))
                ? ((FileSource) source).getFile()
                : new File(key.getConfigId().replace("file:", ""));
        return new FileConfigSubscription<>(key, subscriber, file);
    }

    private static <T extends ConfigInstance> ConfigSubscription<T> getRawSub(ConfigKey<T> key,
                                                                              ConfigSubscriber subscriber,
                                                                              ConfigSource source) {
        String payload = ((source instanceof RawSource)
                ? ((RawSource) source).payload
                : key.getConfigId().replace("raw:", ""));
        return new RawConfigSubscription<>(key, subscriber, payload);
    }

    private static <T extends ConfigInstance> ConfigSubscription<T> getDirFileSub(ConfigKey<T> key,
                                                                                  ConfigSubscriber subscriber,
                                                                                  ConfigSource source) {
        String dir = key.getConfigId().replace("dir:", "");
        if (source instanceof DirSource) {
            dir = ((DirSource) source).getDir().toString();
        }
        if (!dir.endsWith(File.separator)) dir = dir + File.separator;
        String name = getConfigFilename(key);
        File file = new File(dir + name);
        if (!file.exists()) {
            throw new IllegalArgumentException("Could not find a config file for '" + key.getName() + "' in '" + dir + "'");
        }
        return new FileConfigSubscription<>(key, subscriber, file);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (o instanceof ConfigSubscription) {
            ConfigSubscription<T> other = (ConfigSubscription<T>) o;
            return key.equals(other.key) &&
                    subscriber.equals(other.subscriber);
        }
        return false;
    }


    /**
     * Called from {@link ConfigSubscriber} when the changed status of this config is propagated to the clients
     */
    public boolean isConfigChangedAndReset(Long requiredGen) {
        ConfigState<T> prev = config.get();
        while (prev.getGeneration().equals(requiredGen) && !config.compareAndSet(prev, prev.createUnchanged())) {
            prev = config.get();
        }
        // A false positive is a lot better than a false negative
        return !prev.getGeneration().equals(requiredGen) || prev.isConfigChanged();
    }

    void setConfig(Long generation, boolean applyOnRestart, T config, PayloadChecksum payloadChecksum) {
        this.config.set(new ConfigState<>(true, generation, applyOnRestart, true, config, payloadChecksum));
    }

    /**
     * Used by {@link FileConfigSubscription} and {@link ConfigSetSubscription}
     */
    protected void setConfigIncGen(T config) {
        ConfigState<T> prev = this.config.get();
        this.config.set(new ConfigState<>(true, prev.getGeneration() + 1, prev.applyOnRestart(), true, config, prev.payloadChecksum));
    }

    protected void setConfigIfChanged(T config) {
        ConfigState<T> prev = this.config.get();
        this.config.set(new ConfigState<>(true, prev.getGeneration(), prev.applyOnRestart(), !config.equals(prev.getConfig()), config, prev.payloadChecksum));
    }

    void setGeneration(Long generation) {
        ConfigState<T> prev = config.get();
        this.config.set(new ConfigState<>(true, generation, prev.applyOnRestart(), prev.isConfigChanged(), prev.getConfig(), prev.payloadChecksum));
    }

    void setApplyOnRestart(boolean applyOnRestart) {
        ConfigState<T> prev = config.get();
        this.config.set(new ConfigState<>(prev.isGenerationChanged(), prev.getGeneration(), applyOnRestart, prev.isConfigChanged(), prev.getConfig(), prev.payloadChecksum));
    }

    /**
     * The config state object of this subscription
     *
     * @return the ConfigInstance (the config) of this subscription
     */
    public ConfigState<T> getConfigState() {
        return config.get();
    }

    /**
     * The class of the subscription's desired {@link ConfigInstance}
     *
     * @return the config class
     */
    public Class<T> getConfigClass() {
        return configClass;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(key.toString());
        ConfigState<T> c = config.get();
        s.append(", Current generation: ").append(c.getGeneration())
                .append(", Generation changed: ").append(c.isGenerationChanged())
                .append(", Config changed: ").append(c.isConfigChanged());
        if (exception != null)
            s.append(", Exception: ").append(exception);
        return s.toString();
    }

    /**
     * The config key which this subscription uses to identify its config
     *
     * @return the ConfigKey for this subscription
     */
    public ConfigKey<T> getKey() {
        return key;
    }

    /**
     * Polls this subscription for a change. The method is guaranteed to use all of the given timeout before returning false. It will also take into account a user-set generation,
     * that can be set by {@link ConfigSubscriber#reload(long)}.
     *
     * @param timeout in milliseconds
     * @return false if timed out, true if generation or config or {@link #exception} changed. If true, the {@link #config} field will be set also.
     * has changed
     */
    public abstract boolean nextConfig(long timeout);

    /**
     * Will block until the next {@link #nextConfig(long)} is guaranteed to return an answer (or throw) immediately (i.e. not block)
     *
     * @param timeout in milliseconds
     * @return false if timed out
     */
    public abstract boolean subscribe(long timeout);

    /**
     * Called by for example network threads to signal that the user thread should throw this exception immediately
     *
     * @param e a RuntimeException
     */
    public void setException(RuntimeException e) {
        this.exception = e;
    }

    /**
     * Gets an exception set by for example a network thread. If not null, it indicates that it should be
     * thrown in the user's thread immediately.
     *
     * @return a RuntimeException if there exists one
     */
    public RuntimeException getException() {
        return exception;
    }

    /**
     * Returns true if an exception set by for example a network thread has been caught.
     *
     * @return true if there exists an exception for this subscription
     */
    boolean hasException() {
        return exception != null;
    }

    public void close() {
        state = State.CLOSED;
    }

    State getState() {
        return state;
    }

    /**
     * Returns the file name corresponding to the given key's defName.
     *
     * @param key a {@link ConfigKey}
     * @return file name
     */
    static <T extends ConfigInstance> String getConfigFilename(ConfigKey<T> key) {
        return key.getName() + ".cfg";
    }

    /**
     * Force this into the given generation, used in testing
     *
     * @param generation a config generation
     */
    public void reload(long generation) {
        reloadedGeneration.set(generation);
    }

    /**
     * True if someone has set the {@link #reloadedGeneration} number by calling {@link #reload(long)}
     * and hence wants to force a given generation programmatically. If that is the case,
     * sets the generation and flags it as changed accordingly.
     *
     * @return true if {@link #reload(long)} has been called, false otherwise
     */
    protected boolean checkReloaded() {
        Long reloaded = reloadedGeneration.getAndSet(null);
        if (reloaded != null) {
            setGeneration(reloaded);
            return true;
        }
        return false;
    }

    /**
     * The config definition schema
     *
     * @return the config definition for this subscription
     */
    public DefContent getDefContent() {
        return (DefContent.fromClass(configClass));
    }
}
