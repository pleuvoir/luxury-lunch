package io.github.pleuvoir.tookit.configuration.dynamic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;


public class DefaultDynamicConfig implements DynamicConfig {

	private final String name;
	private final CopyOnWriteArrayList<DynamicConfigListener> listeners;
	private volatile File file;
	private volatile boolean loaded = false;
	private volatile Map<String, String> config;

	DefaultDynamicConfig(String name, boolean failOnNotExist) {
		this.name = name;
		this.listeners = new CopyOnWriteArrayList<>();
		this.file = getFileByName(name);

		if (failOnNotExist && (file == null || !file.exists())) {
			throw new RuntimeException("cannot find config file " + name);
		}
	}

	private File getFileByName(final String name) {
		try {
			final URL res = this.getClass().getClassLoader().getResource(name);
			if (res == null) {
				return null;
			}
			return Paths.get(res.toURI()).toFile();
		} catch (URISyntaxException e) {
			throw new RuntimeException("load config file failed", e);
		}
	}

	long getLastModified() {
		if (file == null) {
			file = getFileByName(name);
		}
		if (file == null) {
			return 0;
		} else {
			return file.lastModified();
		}
	}

	synchronized void onConfigModified() {
		if (file == null) {
			return;
		}
		loadConfig();
		executeListeners();
		loaded = true;
	}

	private void loadConfig() {
		try {
			final Properties p = new Properties();
			try (Reader reader = new BufferedReader(new FileReader(file))) {
				p.load(reader);
			}
			final Map<String, String> map = new LinkedHashMap<>(p.size());
			for (String key : p.stringPropertyNames()) {
				map.put(key, tryTrim(p.getProperty(key)));
			}
			config = Collections.unmodifiableMap(map);
		} catch (IOException e) {
			throw new RuntimeException("load config file failed", e);
		}
	}

	private String tryTrim(String data) {
		if (data == null) {
			return null;
		} else {
			return data.trim();
		}
	}

	private void executeListeners() {
		for (DynamicConfigListener listener : listeners) {
			executeListener(listener);
		}
	}

	@Override
	public void addListener(DynamicConfigListener listener) {
		if (loaded) {
			executeListener(listener);
		}
		listeners.add(listener);
	}

	private void executeListener(DynamicConfigListener listener) {
		try {
			listener.onLoad(this);
		} catch (Throwable e) {
			throw new RuntimeException("trigger config listener failed. config: " + name, e);
		}
	}

	@Override
	public String getString(String name) {
		return getValueWithCheck(name);
	}

	@Override
	public String getString(String name, String defaultValue) {
		String value = getValue(name);
		if (isBlank(value))
			return defaultValue;
		return value;
	}

	@Override
	public int getInt(String name) {
		return Integer.valueOf(getValueWithCheck(name));
	}

	@Override
	public int getInt(String name, int defaultValue) {
		String value = getValue(name);
		if (isBlank(value))
			return defaultValue;
		return Integer.valueOf(value);
	}

	@Override
	public long getLong(String name) {
		return Long.valueOf(getValueWithCheck(name));
	}

	@Override
	public long getLong(String name, long defaultValue) {
		String value = getValue(name);
		if (isBlank(value))
			return defaultValue;
		return Long.valueOf(value);
	}

	@Override
	public double getDouble(final String name) {
		return Double.valueOf(getValueWithCheck(name));
	}

	@Override
	public double getDouble(final String name, final double defaultValue) {
		String value = getValue(name);
		if (isBlank(value))
			return defaultValue;
		return Double.valueOf(value);
	}

	@Override
	public boolean getBoolean(String name, boolean defaultValue) {
		String value = getValue(name);
		if (isBlank(value))
			return defaultValue;
		return Boolean.valueOf(value);
	}

	private String getValueWithCheck(String name) {
		String value = getValue(name);
		if (isBlank(value)) {
			throw new RuntimeException("配置项: " + name + " 值为空");
		} else {
			return value;
		}
	}

	private String getValue(String name) {
		return config.get(name);
	}

	private boolean isBlank(final String s) {
		if (s == null || s.isEmpty()) {
			return true;
		}
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isWhitespace(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean exist(String name) {
		return config.containsKey(name);
	}

	@Override
	public Map<String, String> toMap() {
		return new HashMap<>(config);
	}

	@Override
	public String toString() {
		return String.format("LocalDynamicConfig [name=%s, listeners=%s, file=%s, loaded=%s, config=%s]", name,
				listeners, file, loaded, config);
	}

}