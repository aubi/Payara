/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.nucleus.microprofile.config.spi;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Implementation for the {@link ConfigValueResolver} which uses the non public API of
 * {@link PayaraConfig#getValue(String, String, Long, String, Supplier)} to implement its utility methods.
 *
 * @author Jan Bernitt
 */
final class ConfigValueResolverImpl implements ConfigValueResolver {

    private final PayaraConfig config;
    private final String propertyName;
    private boolean throwsOnMissingProperty;
    private boolean throwOnFailedConversion;
    private Long ttl;
    private String rawDefault;

    ConfigValueResolverImpl(PayaraConfig config, String propertyName) {
        this.config = config;
        this.propertyName = propertyName;
    }

    @Override
    public ConfigValueResolver withTTL(long ttl) {
        this.ttl = ttl < 0 ? null : ttl;
        return this;
    }

    @Override
    public ConfigValueResolver withDefault(String value) {
        rawDefault = ConfigProperty.UNCONFIGURED_VALUE.equals(value) ? null : value;
        return this;
    }

    @Override
    public ConfigValueResolver throwOnMissingProperty(boolean throwOnMissingProperty) {
        this.throwsOnMissingProperty = throwOnMissingProperty;
        return this;
    }

    @Override
    public ConfigValueResolver throwOnFailedConversion(boolean throwOnFailedConversion) {
        this.throwOnFailedConversion = throwOnFailedConversion;
        return this;
    }

    @Override
    public <T> T as(Class<T> type, T defaultValue) {
        return asValue(propertyName, getCacheKey(propertyName, type), ttl, defaultValue, () -> config.getConverter(type));
    }

    @Override
    public <T> Optional<T> as(Class<T> type) {
        return Optional.ofNullable(as(type, null));
    }

    @Override
    public <E> List<E> asList(Class<E> elementType) {
        return asList(elementType, emptyList());
    }

    @Override
    public <E> List<E> asList(Class<E> elementType, List<E> defaultValue) {
        return asValue(propertyName, getCacheKey(propertyName, List.class, elementType), ttl, defaultValue,
                () -> createListConverter(getArrayConverter(elementType)));
    }

    @Override
    public <E> Set<E> asSet(Class<E> elementType) {
        return asSet(elementType, emptySet());
    }

    @Override
    public <E> Set<E> asSet(Class<E> elementType, Set<E> defaultValue) {
        return asValue(propertyName, getCacheKey(propertyName, Set.class, elementType), ttl, defaultValue,
                () -> createSetConverter(getArrayConverter(elementType)));
    }

    @Override
    public <T> T asConvertedBy(Function<String, T> converter, T defaultValue) {
        String sourceValue = asValue(propertyName, getCacheKey(propertyName, String.class), ttl, null,
                () -> value -> value);
        if (sourceValue == null) {
            if (throwsOnMissingProperty) {
                throwWhenNotExists(propertyName, null);
            }
            return defaultValue;
        }
        try {
            return converter.apply(sourceValue);
        } catch (Exception ex) {
            if (rawDefault != null) {
                try {
                    return converter.apply(rawDefault);
                } catch (Exception e) {
                    // fall through
                }
            }
            if (throwOnFailedConversion) {
                throw new IllegalArgumentException(ex);
            }
            return defaultValue;
        }
    }

    private <T> T asValue(String propertyName, String cacheKey, Long ttl, T defaultValue, Supplier<? extends Converter<T>> converter) {
        try {
            T value = config.getValue(propertyName, cacheKey, ttl, getRawDefault(), converter);
            if (value != null) {
                return value;
            }
            if (throwsOnMissingProperty) {
                throwWhenNotExists(propertyName, null);
            }
            return defaultValue;
        } catch (IllegalArgumentException ex) {
            if (throwOnFailedConversion) {
                throw ex;
            }
            return defaultValue;
        }
    }

    private String getRawDefault() {
        return throwsOnMissingProperty ? null : rawDefault;
    }

    private <E> Converter<E[]> getArrayConverter(Class<E> elementType) {
        return config.getConverter(arrayTypeOf(elementType));
    }

    static void throwWhenNotExists(String propertyName, Object value) {
        if (value == null) {
            throw new NoSuchElementException("Unable to find property with name " + propertyName);
        }
    }

    static String getCacheKey(String propertyName, Class<?> propertyType) {
        String key = propertyType.getName() + ":" + propertyName;
        return key;
    }

    static <E> String getCacheKey(String propertyName, Class<?> collectionType, Class<E> elementType) {
        return collectionType.getName() + ":" + getCacheKey(propertyName, elementType);
    }

    static <E> Converter<List<E>> createListConverter(Converter<E[]> arrayConverter) {
        return sourceValue -> Arrays.asList(arrayConverter.convert(sourceValue));
    }

    static <E> Converter<Set<E>> createSetConverter(Converter<E[]> arrayConverter) {
        return sourceValue ->  new HashSet<>(Arrays.asList(arrayConverter.convert(sourceValue)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <E> Class<E[]> arrayTypeOf(Class<E> elementType) {
        if (elementType.isPrimitive()) {
            return (Class) arrayTypeOf(PayaraConfig.boxedTypeOf(elementType));
        }
        return (Class<E[]>) Array.newInstance(elementType, 0).getClass();
    }
}
