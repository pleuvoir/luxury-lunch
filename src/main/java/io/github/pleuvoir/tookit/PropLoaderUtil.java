package io.github.pleuvoir.tookit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * properties 文件工具
 * @author pleuvoir
 *
 */
public abstract class PropLoaderUtil {

	/**
	 * 加载 properties 文件，可以使用绝对路径或者相对路径
	 * 
	 * <p>
	 * 注意：基于相对路径的读取依赖于 getResourceAsStream 方法，所以读取的文件路径只局限于工程的源文件夹中，包括在工程src根目录下，以及类包里面任何位置，
	 * 如果配置文件路径是在除了源文件夹之外的其他文件夹中时，该方法是用不了的。
	 * <p>
	 * @param propertyFileName	文件名称，可以使用绝对路径或者相对路径，如文件在 classpath 下，可以使用 / 开头读取
	 * @return	返回设值后的 Properties 对象
	 */
	public static Properties loadProperties(String propertyFileName) {
		Properties props = new Properties();
		final File propFile = new File(propertyFileName);
		try (final InputStream is = propFile.isFile() ? new FileInputStream(propFile): PropLoaderUtil.class.getResourceAsStream(propertyFileName)) {
			if (is != null) {
				props.load(is);
			} else {
				throw new IllegalArgumentException("Cannot find property file: " + propertyFileName);
			}
		} catch (IOException io) {
			throw new RuntimeException("Failed to read property file", io);
		}
		return props;
	}
	
	/**
	 * 复制 Properties 内容并返回一个新的 Properties 对象
	 * @param props	原 Properties 对象
	 * @return	赋值后新的 Properties 对象
	 */
	public static Properties copyProperties(final Properties props) {
		Properties copy = new Properties();
		props.forEach((key, value) -> copy.setProperty(key.toString(), value.toString()));
		return copy;
	}

	// ####### 完成文件到对象的转换
	
	/**
	 * 完成文件到对象的转换 <br>
	 * 注意：尝试使用类中变量名进行转换，如果类型为布尔且以 is 开头，请配置 properties 文件时去除 is，否则会报错
	 * @param target	待转换的对象
	 * @param propertyFileName 配置文件名称
	 * @param ignorePrefix 忽略前缀
	 * @return	转换后的对象
	 */
	public static <T> T setTargetFromProperties(final T target, final String propertyFileName, String ignorePrefix) {
		if (target == null || propertyFileName == null) {
			throw new IllegalArgumentException("target or propertyFileName must be non-null.");
		}
		List<Method> methods = Arrays.asList(target.getClass().getMethods());
		Properties props = loadProperties(propertyFileName);
		Iterator<Entry<Object, Object>> iterator = props.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>) iterator.next();
			String key = entry.getKey().toString();
			Object value = entry.getValue();
			if (key.startsWith(ignorePrefix)) { // 对前缀进行忽略处理
				key = key.substring(ignorePrefix.length());
			} 
			setProperty(target, key, value, methods);
		}
		return target;
	}
	
	/**
	 * 完成文件到对象的转换 <br>
	 * 注意：尝试使用类中变量名进行转换，如果类型为布尔且以 is 开头，请配置 properties 文件时去除 is，否则会报错
	 * @param target	待转换的对象
	 * @param propertyFileName 配置文件名称
	 * @return	转换后的对象
	 */
	public static <T> T setTargetFromProperties(final T target, final String propertyFileName) {
		if (target == null || propertyFileName == null) {
			 throw new IllegalArgumentException("target or propertyFileName must be non-null.");
		}
		List<Method> methods = Arrays.asList(target.getClass().getMethods());
		loadProperties(propertyFileName).forEach((key, value) -> {
			setProperty(target, key.toString(), value, methods);
		});
		return target;
	}
	
	/**
	 * 完成文件到对象的转换
	 * @param target	待转换的对象
	 * @param properties 配置文件
	 * @return	转换后的对象
	 */
	public static <T> T setTargetFromProperties(final T target, final Properties properties) {
		if (target == null || properties == null) {
			 throw new IllegalArgumentException("target or properties must be non-null.");
		}
		List<Method> methods = Arrays.asList(target.getClass().getMethods());
		properties.forEach((key, value) -> {
			setProperty(target, key.toString(), value, methods);
		});
		return target;
	}
	
	// 尝试使用类中变量名进行转换，如果类型为布尔且以 is 开头，请配置 properties 文件时去除 is，否则会报错
	private static void setProperty(final Object target, final String propName, final Object propValue,
			final List<Method> methods) {

	      String methodName = "set" + propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
	      Method writeMethod = methods.stream().filter(m -> m.getName().equals(methodName) && m.getParameterCount() == 1).findFirst().orElse(null);

	      // 这里布尔类型会有问题，建议在类中设置布尔类型的变量时是不要以 is 开头，如果已经使用了 is 开头，那么 properties 文件中的 key 请去掉 is
	      if (writeMethod == null) {
	         String booleanMethodName =  "is" + propName.substring(0, 1).toUpperCase(Locale.ENGLISH) + propName.substring(1);
	         writeMethod = methods.stream().filter(m -> m.getName().equals(booleanMethodName) && m.getParameterCount() == 1).findFirst().orElse(null);
	      }

	      if (writeMethod == null) {
	         throw new RuntimeException(String.format("Property %s does not exist on target %s", propName, target.getClass()));
	      }

	      try {
	    	  // 根据参数类型尝试
	         Class<?> paramClass = writeMethod.getParameterTypes()[0];
	         if (paramClass == int.class) {
	            writeMethod.invoke(target, Integer.parseInt(propValue.toString()));
	         }
	         else if (paramClass == long.class) {
	            writeMethod.invoke(target, Long.parseLong(propValue.toString()));
	         }
	         else if (paramClass == boolean.class || paramClass == Boolean.class) {
	            writeMethod.invoke(target, Boolean.parseBoolean(propValue.toString()));
	         }
	         else if (paramClass == String.class) {
	            writeMethod.invoke(target, propValue.toString());
	         }
			 else if (paramClass == double.class) {
				writeMethod.invoke(target, Double.valueOf(propValue.toString()));
			 }
	         else {
	            try {
	               writeMethod.invoke(target, Class.forName(propValue.toString()).newInstance());
	            }
	            catch (InstantiationException | ClassNotFoundException e) {
	               writeMethod.invoke(target, propValue);
	            }
	         }
	      }
	      catch (Exception e) {
	         throw new RuntimeException(e);
	      }
	   }
}
