/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl.inject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.scr.impl.helper.ReadOnlyDictionary;
import org.apache.felix.scr.impl.inject.internal.Annotations;
import org.apache.felix.scr.impl.inject.internal.ClassUtils;
import org.apache.felix.scr.impl.logger.ComponentLogger;
import org.apache.felix.scr.impl.logger.InternalLogger.Level;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

/**
 * Utility methods for handling references and activation
 */
public class ValueUtils {

    /**
     * The value type of the field, activation field or constructor parameter
     */
    public enum ValueType
    {
        ignore,
        componentContext,       // field activation, constructor
        bundleContext,          // field activation, constructor
        config_map,             // field activation, constructor
        config_annotation,      // field activation, constructor
        ref_logger,             // reference (field, constructor, method)
        ref_formatterLogger,    // reference (field, constructor, method)
        ref_serviceReference,   // reference (field, constructor, method)
        ref_serviceObjects,     // reference (field, constructor, method)
        ref_serviceType,        // reference (field, constructor, method)
        ref_map,                // reference (field, constructor, method)
        ref_tuple, // reference (field, constructor ??) // TDODO
        ref_optional // reference (field, constructor, XX)
    }

    /** Empty array. */
    public static final ValueType[] EMPTY_VALUE_TYPES = new ValueType[0];

    /**
     * Get the value type for the parameter class.
     * This method is used for field activation and constructor injection.
     *
     * @param typeClass The class of the parameter
     * @return The value type
     */
    public static ValueType getValueType( final Class<?> typeClass )
    {
        if ( typeClass == ClassUtils.COMPONENT_CONTEXT_CLASS )
        {
            return ValueType.componentContext;
        }
        else if ( typeClass == ClassUtils.BUNDLE_CONTEXT_CLASS )
        {
            return ValueType.bundleContext;
        }
        else if ( typeClass == ClassUtils.MAP_CLASS )
        {
            return ValueType.config_map;
        }
        else if ( typeClass.isAnnotation() )
        {
            return ValueType.config_annotation;
        }
        return ValueType.ignore;
    }

    /**
     * Get the value type of the reference for a field/constructor argument
     *
     * @param componentClass The component class declaring the reference
     * @param metadata The reference metadata
     * @param typeClass The type of the field/parameter
     * @param f The optional field. If {@code null} this is a constructor reference
     * @param logger The logger
     * @return The value type for the field. If invalid, {@code ValueType#ignore}
     */
    public static ValueType getReferenceValueType(
            final Class<?> componentClass,
            final ReferenceMetadata metadata,
            final Class<?> typeClass,
            final Field field,
            final ComponentLogger logger )
    {
        final Class<?> referenceType = ClassUtils.getClassFromComponentClassLoader(
                componentClass, metadata.getInterface(), logger);

        ValueType valueType = ValueType.ignore;

        // unary reference
        if ( !metadata.isMultiple() )
        {
            // service interface or supertype
            if ( typeClass.isAssignableFrom(referenceType) )
            {
                valueType = ValueType.ref_serviceType;
            }
            // service reference
            else if ( typeClass == ClassUtils.SERVICE_REFERENCE_CLASS )
            {
                valueType = ValueType.ref_serviceReference;
            }
            // components service object
            else if ( typeClass == ClassUtils.COMPONENTS_SERVICE_OBJECTS_CLASS )
            {
                valueType = ValueType.ref_serviceObjects;
            }
            // map (properties)
            else if ( typeClass == ClassUtils.MAP_CLASS )
            {
                valueType = ValueType.ref_map;
            }
            // tuple (map.entry)
            else if ( typeClass == ClassUtils.MAP_ENTRY_CLASS )
            {
                valueType = ValueType.ref_tuple;
            }
            // 1.4: Logger - reference needs to be of type LoggerFactory
            else if ( typeClass.getName().equals(ClassUtils.LOGGER_CLASS) && metadata.getInterface().equals(ClassUtils.LOGGER_FACTORY_CLASS) )
            {
                return ValueType.ref_logger;
            }
            // 1.4: FormatterLogger - reference needs to be of type LoggerFactory
            else if ( typeClass.getName().equals(ClassUtils.FORMATTER_LOGGER_CLASS) && metadata.getInterface().equals(ClassUtils.LOGGER_FACTORY_CLASS) )
            {
                return ValueType.ref_formatterLogger;
            }
            // 1.5 Optional
            else if (typeClass == ClassUtils.OPTIONAL_CLASS)
            {
                // Note that the first check for "typeClass.isAssignableFrom(referenceType)"
                // will handle case where they want an actual Optional service type.

                valueType = ValueType.ref_optional;
            }
            else
            {
                if ( field != null )
                {
                    logger.log(Level.ERROR,
                        "Field {0} in class {1} has unsupported type {2}", null,
                            metadata.getField(), componentClass, typeClass.getName() );
                }
                else
                {
                    logger.log(Level.ERROR,
                        "Constructor argument {0} in class {1} has unsupported type {2}",
                        null,
                            metadata.getParameterIndex(), componentClass, typeClass.getName() );
                }
                valueType = ValueType.ignore;
            }

            // if the field is dynamic, it has to be volatile (field is ignored, case logged) (112.3.8.1)
            if ( field != null && !metadata.isStatic() && !Modifier.isVolatile(field.getModifiers()) ) {
                logger.log(Level.ERROR,
                    "Field {0} in class {1} must be declared volatile to handle a dynamic reference",
                    null,
                        metadata.getField(), componentClass );
                valueType = ValueType.ignore;
            }

            // the field must not be final (field is ignored, case logged) (112.3.8.1)
            if ( field != null && Modifier.isFinal(field.getModifiers()) )
            {
                logger.log(Level.ERROR,
                    "Field {0} in class {1} must not be declared as final", null,
                        metadata.getField(), componentClass );
                valueType = ValueType.ignore;
            }
        }
        else
        {
            String colType = metadata.getCollectionType();
            valueType = getCollectionValueType(colType);

            // multiple cardinality, field type must be collection or subtype
            if ( !ClassUtils.COLLECTION_CLASS.isAssignableFrom(typeClass) )
            {
                if ( field != null )
                {
                    logger.log(Level.ERROR,
                        "Field {0} in class {1} has unsupported type {2}", null,
                            metadata.getField(), componentClass, typeClass.getName() );
                }
                else
                {
                    logger.log(Level.ERROR,
                        "Constructor argument {0} in class {1} has unsupported type {2}",
                        null,
                            metadata.getParameterIndex(), componentClass, typeClass.getName() );
                }
                valueType = ValueType.ignore;
            }

            // additional checks for replace strategy:
            if ( metadata.isReplace() && field != null )
            {
                // if the field is dynamic wit has to be volatile (field is ignored, case logged) (112.3.8.1)
                if ( !metadata.isStatic() && !Modifier.isVolatile(field.getModifiers()) )
                {
                    logger.log(Level.ERROR,
                        "Field {0} in class {1} must be declared volatile to handle a dynamic reference",
                        null,
                            metadata.getField(), componentClass );
                    valueType = ValueType.ignore;
                }

                // replace strategy: field must not be final (field is ignored, case logged) (112.3.8.1)
                //                   only collection and list allowed
                if ( typeClass != ClassUtils.LIST_CLASS && typeClass != ClassUtils.COLLECTION_CLASS )
                {
                    logger.log(Level.ERROR,
                        "Field {0} in class {1} has unsupported type {2}." +
                            " It must be one of java.util.Collection or java.util.List.", null,
                            metadata.getField(), componentClass, typeClass.getName() );
                    valueType = ValueType.ignore;

                }
                if ( Modifier.isFinal(field.getModifiers()) )
                {
                    logger.log(Level.ERROR,
                        "Field {0} in class {1} must not be declared as final", null,
                            metadata.getField(), componentClass );
                    valueType = ValueType.ignore;
                }
            }
        }
        // static references only allowed for replace strategy
        if ( field != null && metadata.isStatic() && !metadata.isReplace() )
        {
            logger.log(Level.ERROR,
                "Update strategy for field {0} in class {1} only allowed for non static field references.",
                null,
                    metadata.getField(), componentClass );
            valueType = ValueType.ignore;
        }
        return valueType;
    }

    public static ValueType getCollectionValueType(String colType)
    {
        if (colType == null)
        {
            return ValueType.ref_serviceType;
        }
        switch (colType)
        {
            case ReferenceMetadata.FIELD_VALUE_TYPE_SERVICE:
                return ValueType.ref_serviceType;
            case ReferenceMetadata.FIELD_VALUE_TYPE_REFERENCE:
                return ValueType.ref_serviceReference;
            case ReferenceMetadata.FIELD_VALUE_TYPE_SERVICEOBJECTS:
                return ValueType.ref_serviceObjects;
            case ReferenceMetadata.FIELD_VALUE_TYPE_PROPERTIES:
                return ValueType.ref_map;
            case ReferenceMetadata.FIELD_VALUE_TYPE_TUPLE:
                return ValueType.ref_tuple;
            default:
                return ValueType.ignore;
        }
    }

    /**
     * Get the value for the value type
     * @param componentType The class of the component
     * @param type The value type
     * @param targetType Optional target type, only required for type {@code ValueType#config_annotation}.
     * @param componentContext The component context
     * @param refPair The ref pair
     * @return The value or {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static Object getValue(
        final String componentType,
        final ValueType type,
        final Class<?> targetType,
        final ScrComponentContext componentContext,
        final RefPair<?, ?> refPair,
        final ReferenceMetadata referenceMetaData)
    {
        switch ( type )
        {
            case ignore:
                return null;
            case componentContext:
                return componentContext;
            case bundleContext:
                return componentContext.getBundleContext();
            case config_map:
                // note: getProperties() returns a ReadOnlyDictionary which is a Map
                return componentContext.getProperties();
            case config_annotation:
                return Annotations.toObject(targetType,
                    (Map<String, Object>) componentContext.getProperties(),
                    componentContext.getBundleContext().getBundle(),
                    componentContext.getComponentMetadata().isConfigureWithInterfaces());
            case ref_serviceType:
                return refPair.getServiceObject(componentContext);
            case ref_serviceReference:
                return refPair.getRef();
            case ref_serviceObjects:
                return componentContext.getComponentServiceObjectsHelper().getServiceObjects(
                    refPair.getRef());
            case ref_map:
                return new ReadOnlyDictionary(refPair.getRef());
            case ref_tuple:
                final Object tupleKey = new ReadOnlyDictionary(refPair.getRef());
                final Object tupleValue = refPair.getServiceObject(componentContext);
                return new MapEntryImpl(tupleKey, tupleValue, refPair.getRef());
            case ref_logger:
            case ref_formatterLogger:
                return getLogger(componentType, targetType, componentContext, refPair);
            case ref_optional:
                final String stringType = referenceMetaData == null
                    ? ReferenceMetadata.FIELD_VALUE_TYPE_SERVICE
                    : referenceMetaData.getCollectionType();
                final ValueType optionalValueType = getCollectionValueType(stringType);
                Object value = getValue(componentType, optionalValueType, targetType,
                    componentContext, refPair, referenceMetaData);
                return Optional.ofNullable(value);
            default:
                return null;
        }
    }

    private static Object getLogger(String componentType,
            final Class<?> targetType,
            final ScrComponentContext componentContext,
            final RefPair<?, ?> refPair )
    {
        final Object factory = refPair.getServiceObject(componentContext);
        if ( factory != null )
        {
            Exception error = null;
            try {
                final Method m = factory.getClass().getMethod("getLogger", new Class[] {Bundle.class, String.class, Class.class});
                // FELIX-5905
                m.setAccessible(true);
                return m.invoke(factory, new Object[] {componentContext.getBundleContext().getBundle(), componentType, targetType});
            } catch (final NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                error = e;
            }
            componentContext.getLogger().log(Level.ERROR,
                "Unexpected error while trying to get logger.", null, error);
        }
        return null;
    }

    /**
     * Comparable map entry using the service reference to compare.
     */
    @SuppressWarnings("rawtypes")
    private static final class MapEntryImpl implements Map.Entry, Comparable<Map.Entry<?, ?>>
    {

        private final Object key;
        private final Object value;
        private final ServiceReference<?> ref;

        public MapEntryImpl(final Object key,
                final Object value,
                final ServiceReference<?> ref)
        {
            this.key = key;
            this.value = value;
            this.ref = ref;
        }

        @Override
        public Object getKey()
        {
            return this.key;
        }

        @Override
        public Object getValue()
        {
            return this.value;
        }

        @Override
        public Object setValue(final Object value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(final Map.Entry<?, ?> o)
        {
            if ( o == null )
            {
                return 1;
            }
            if ( o instanceof MapEntryImpl )
            {
                final MapEntryImpl other = (MapEntryImpl)o;
                return ref.compareTo(other.ref);

            }
            return new Integer(this.hashCode()).compareTo(o.hashCode());
        }
    }
}
