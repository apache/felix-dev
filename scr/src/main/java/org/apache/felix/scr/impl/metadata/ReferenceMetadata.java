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
package org.apache.felix.scr.impl.metadata;

import static org.apache.felix.scr.impl.metadata.MetadataStoreHelper.addString;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.felix.scr.impl.metadata.MetadataStoreHelper.MetaDataReader;
import org.apache.felix.scr.impl.metadata.MetadataStoreHelper.MetaDataWriter;

/**
 * Information associated to a dependency
 *
 */
public class ReferenceMetadata
{
	public enum ReferenceScope {bundle, prototype, prototype_required}

    // constant for option single reference - 0..1
    public static final String CARDINALITY_0_1 = "0..1";

    // constant for option multiple reference - 0..n
    public static final String CARDINALITY_0_N = "0..n";

    // constant for required single reference - 1..1
    public static final String CARDINALITY_1_1 = "1..1";

    // constant for required multiple reference - 1..n
    public static final String CARDINALITY_1_N = "1..n";

    // set of valid cardinality settings
    private static final Set<String> CARDINALITY_VALID;

    // constant for static policy
    public static final String POLICY_STATIC = "static";

    // constant for dynamic policy
    public static final String POLICY_DYNAMIC = "dynamic";

    // set of valid policy settings
    private static final Set<String> POLICY_VALID;

    // constant for reluctant policy option
    public static final String POLICY_OPTION_RELUCTANT = "reluctant";

    // constant for greedy policy option
    public static final String POLICY_OPTION_GREEDY = "greedy";

    // set of valid policy option settings
    private static final Set<String> POLICY_OPTION_VALID;

    // constant for update field strategy
    private static final String FIELD_STRATEGY_UPDATE = "update";

    // constant for replace field strategy
    private static final String FIELD_STRATEGY_REPLACE = "replace";

    // set of valid field strategy settings
    private static final Set<String> FIELD_STRATEGY_VALID;

    // constant for field value type service
    public static final String FIELD_VALUE_TYPE_SERVICE = "service";

    // constant for field value type properties
    public static final String FIELD_VALUE_TYPE_PROPERTIES = "properties";

    // constant for field value type reference
    public static final String FIELD_VALUE_TYPE_REFERENCE = "reference";

    // constant for field value type serviceobjects
    public static final String FIELD_VALUE_TYPE_SERVICEOBJECTS = "serviceobjects";

    // constant for field value type tuple
    public static final String FIELD_VALUE_TYPE_TUPLE = "tuple";

    // set of valid field value type settings
    private static final Set<String> FIELD_VALUE_TYPE_VALID;

    public static final String CONDITION_SERVICE_CLASS = "org.osgi.service.condition.Condition";

    public static final String CONDITION_TRUE_FILTER = "(osgi.condition.id=true)";

    // TODO this constant will be defined in the R8 Declarative Services spec
    public static final String REFERENCE_NAME_SATISFYING_CONDITION = "osgi.ds.satisfying.condition";

    // Name for the reference (required)
    private String m_name;

    // Interface name (required)
    private String m_interface;

    // Cardinality (optional, default="1..1")
    private String m_cardinality;

    // Target (optional)
    private String m_target;

    // Name of the bind method (optional)
    private String m_bind;

    // Name of the updated method (optional, since DS 1.1-felix)
    private String m_updated;

    // Name of the unbind method (optional)
    private String m_unbind;

    // Name of the field (optional, since DS 1.3)
    private String m_field;

    // Name of the strategy for the field (optional, since DS 1.3)
    private String m_field_option;

    // Name of the value type for the field (optional, since DS 1.3)
    // Since 1.4 also used for the parameter collection type (constructor)
    private String m_collection_type;

    // Policy attribute (optional, default = static)
    private String m_policy;

    // Policy option attribute (optional, default = reluctant)
    private String m_policy_option;

    private String m_scopeName;
    private ReferenceScope m_scope = ReferenceScope.bundle;

    // Parameter value (optional, since DS 1.4)
    private String m_parameter;

    // Parameter index, set based on {@code m_parameter} after validation
    // (optional, since DS 1.4)
    private Integer m_parameterIndex;

    // Flags that store the values passed as strings
    private boolean m_isStatic = true;
    private boolean m_isOptional = false;
    private boolean m_isMultiple = false;
    private boolean m_isReluctant = true;
    private boolean m_isReplace = true;

    // Flag that is set once the component is verified (its properties cannot be changed)
    private boolean m_validated = false;


    static
    {
        CARDINALITY_VALID = new TreeSet<>();
        CARDINALITY_VALID.add( CARDINALITY_0_1 );
        CARDINALITY_VALID.add( CARDINALITY_0_N );
        CARDINALITY_VALID.add( CARDINALITY_1_1 );
        CARDINALITY_VALID.add( CARDINALITY_1_N );

        POLICY_VALID = new TreeSet<>();
        POLICY_VALID.add( POLICY_DYNAMIC );
        POLICY_VALID.add( POLICY_STATIC );

        POLICY_OPTION_VALID = new TreeSet<>();
        POLICY_OPTION_VALID.add( POLICY_OPTION_RELUCTANT );
        POLICY_OPTION_VALID.add( POLICY_OPTION_GREEDY );

        FIELD_STRATEGY_VALID = new TreeSet<>();
        FIELD_STRATEGY_VALID.add( FIELD_STRATEGY_REPLACE );
        FIELD_STRATEGY_VALID.add( FIELD_STRATEGY_UPDATE );

        FIELD_VALUE_TYPE_VALID = new TreeSet<>();
        FIELD_VALUE_TYPE_VALID.add ( FIELD_VALUE_TYPE_PROPERTIES );
        FIELD_VALUE_TYPE_VALID.add ( FIELD_VALUE_TYPE_REFERENCE );
        FIELD_VALUE_TYPE_VALID.add ( FIELD_VALUE_TYPE_SERVICE );
        FIELD_VALUE_TYPE_VALID.add ( FIELD_VALUE_TYPE_SERVICEOBJECTS );
        FIELD_VALUE_TYPE_VALID.add ( FIELD_VALUE_TYPE_TUPLE );
    }

    /////////////////////////////////////////////// setters ///////////////////////////////////

    /**
     * Setter for the name attribute
     *
     * @param name
     */
    public void setName( String name )
    {
        if ( m_validated )
        {
            return;
        }

        m_name = name;
    }


    /**
     * Setter for the interfaceName attribute
     *
     * @param interfaceName
     */
    public void setInterface( String interfaceName )
    {
        if ( m_validated )
        {
            return;
        }

        m_interface = interfaceName;

    }


    /**
     * Setter for the cardinality attribute
     *
     * @param cardinality
     */
    public void setCardinality( String cardinality )
    {
        if ( m_validated )
        {
            return;
        }

        m_cardinality = cardinality;

        // secondary properties
        m_isOptional = CARDINALITY_0_1.equals( cardinality ) || CARDINALITY_0_N.equals( cardinality );
        m_isMultiple = CARDINALITY_0_N.equals( cardinality ) || CARDINALITY_1_N.equals( cardinality );
    }


    /**
     *	Setter for the policy attribute
     *
     * @param policy
     */
    public void setPolicy( String policy )
    {
        if ( m_validated )
        {
            return;
        }

        m_policy = policy;

        // secondary property
        m_isStatic = POLICY_STATIC.equals( policy );
    }


    /**
     *	Setter for the policy option attribute
     *
     * @param policyOption
     */
    public void setPolicyOption( String policyOption )
    {
        if ( m_validated )
        {
            return;
        }

        m_policy_option = policyOption;

        // secondary property
        m_isReluctant = POLICY_OPTION_RELUCTANT.equals( policyOption );
    }


    /**
     * Setter for the target attribute (filter)
     *
     * @param target
     */
    public void setTarget( String target )
    {
        if ( m_validated )
        {
            return;
        }

        m_target = ( target == null || target.length() == 0 ) ? null : target;
    }


    /**
     * Setter for the bind method attribute
     *
     * @param bind
     */
    public void setBind( String bind )
    {
        if ( m_validated )
        {
            return;
        }

        m_bind = bind;
    }


    /**
     * Setter for the updated method attribute
     *
     * @param updated
     */
    public void setUpdated( String updated )
    {
        if ( m_validated )
        {
            return;
        }

        m_updated = updated;
    }


    /**
     * Setter for the unbind method attribute
     *
     * @param unbind
     */
    public void setUnbind( String unbind )
    {
        if ( m_validated )
        {
            return;
        }

        m_unbind = unbind;
    }


    /**
     * Setter for the field attribute
     *
     * @param field the field name
     */
    public void setField( final String field )
    {
        if ( m_validated )
        {
            return;
        }

        m_field = field;
    }

    /**
     * Setter for the field strategy attribute
     *
     * @param strategy the field strategy
     */
    public void setFieldOption( final String strategy )
    {
        if ( m_validated )
        {
            return;
        }

        m_field_option = strategy;

        m_isReplace = FIELD_STRATEGY_REPLACE.equals(strategy);
    }

    /**
     * Setter for the field value type attribute
     *
     * @param valuetype the field value type
     */
    public void setCollectionType(final String valuetype)
    {
        if ( m_validated )
        {
            return;
        }

        m_collection_type = valuetype;
    }

    public void setScope(String scopeName) {
        if ( m_validated )
        {
            return;
        }
		this.m_scopeName = scopeName;
	}

    /**
     * Setter for the parameter value
     * DS 1.4
     * @param attribute value
     */
	public void setParameter(String val) {
        if ( m_validated )
        {
            return;
        }
		this.m_parameter = val;
	}

    /////////////////////////////////////////////// getters ///////////////////////////////////

	/**
     * Returns the name of the reference
     *
     * @return A string containing the reference's name
    **/
    public String getName()
    {
        return m_name;
    }


    /**
     * Returns the fully qualified name of the class that is used by the component to access the service
     *
     * @return A string containing a fully qualified name
    **/
    public String getInterface()
    {
        return m_interface;
    }


    /**
     * Get the cardinality as a string
     *
     * @return A string with the cardinality
    **/
    public String getCardinality()
    {
        return m_cardinality;
    }


    /**
     * Get the policy as a string
     *
     * @return A string with the policy
    **/
    public String getPolicy()
    {
        return m_policy;
    }


    /**
     * Get the policy option as a string
     *
     * @return A string with the policy option
    **/
    public String getPolicyOption()
    {
        return m_policy_option;
    }


    /**
     * Returns the filter expression that further constrains the set of target services
     *
     * @return A string with a filter
    **/
    public String getTarget()
    {
        return m_target;
    }


    /**
     * Get the name of a method in the component implementation class that is used to notify that
     * a service is bound to the component configuration
     *
     * @return a String with the name of the bind method
    **/
    public String getBind()
    {
        return m_bind;
    }


    /**
     * Get the name of a method in the component implementation class that is used to notify that
     * the service properties of a bound service have been updated
     *
     * @return a String with the name of the updated method
     **/
    public String getUpdated()
    {
        return m_updated;
    }


    /**
     * Get the name of a method in the component implementation class that is used to notify that
     * a service is unbound from the component configuration
     *
     * @return a String with the name of the unbind method
    **/
    public String getUnbind()
    {
        return m_unbind;
    }


    /**
     * Get the name of a field in the component implementation class that is used to hold
     * the reference
     *
     * @return a String with the name of the field
     */
    public String getField()
    {
        return m_field;
    }


    /**
     * Get the strategy of a field in the component implementation class that is used to hold
     * the reference
     *
     * @return a String with the strategy name for the field
     */
    public String getFieldOption()
    {
        return m_field_option;
    }

    /**
     * Get the value type of a field in the component implementation class that is used to hold
     * the reference
     *
     * @return a String with the value type for the field
     */
    public String getCollectionType()
    {
        return m_collection_type;
    }

    /**
     * Get the parameter index, if specified.
     * This method returns the correct value only after this metadata object has been validated
     * by a call to {@link #validate(ComponentMetadata, Logger)} and the validation has been
     * successful.
     * DS 1.4
     * @return The parameter index , if no parameter is set this returns {@code -null}
     */
    public Integer getParameterIndex()
    {
    	return m_parameterIndex;
    }

    // Getters for boolean values that determine both policy and cardinality

    /**
     * Test if dependency's binding policy is static
     *
     * @return true if static
    **/
    public boolean isStatic()
    {
        return m_isStatic;
    }

    /**
     * Test if dependency is optional (0..1 or 0..n)
     *
     * @return true if the dependency is optional
    **/
    public boolean isOptional()
    {
        return m_isOptional;
    }


    /**
     * Test if dependency is multiple (0..n or 1..n)
     *
     * @return true if the dependency is multiple
    **/
    public boolean isMultiple()
    {
        return m_isMultiple;
    }


    /**
     * Test if policy option is reluctant
     *
     * @return true if policy option is reluctant
     */
    public boolean isReluctant()
    {
        return m_isReluctant;
    }

    /**
     * Test if field strategy is replace.
     *
     * @return true if field strategy is replace
     */
    public boolean isReplace()
    {
        return m_isReplace;
    }

    /**
     * Returns the name of the component property referring to the {@link #getTarget() target}
     * property of this reference.
     *
     * @return the name of the target property which is the name of this referene
     *      suffixed with the string ".target".
     */
    public String getTargetPropertyName()
    {
        return getName() + ".target";
    }

    public String getMinCardinalityName()
    {
        return getName() + ".cardinality.minimum";
    }


    public ReferenceScope getScope() {
		return m_scope;
	}

    /**
     *  Method used to verify if the semantics of this metadata are correct
     *
     */
    void validate(final ComponentMetadata componentMetadata )
    {
        final DSVersion dsVersion = componentMetadata.getDSVersion();

        if ( m_name == null )
        {
            // 112.10 name attribute is optional, defaults to interface since DS 1.1
            if ( !dsVersion.isDS11() )
            {
                throw componentMetadata.validationFailure( "A name must be declared for the reference" );
            }
            setName( getInterface() );
        }

        if ( m_interface == null )
        {
            throw componentMetadata.validationFailure( "An interface must be declared for the reference" );
        }


        if ( m_cardinality == null )
        {
            setCardinality( CARDINALITY_1_1 );
        }
        else if ( !CARDINALITY_VALID.contains( m_cardinality ) )
        {
            throw componentMetadata.validationFailure( "Cardinality must be one of " + CARDINALITY_VALID );
        }

        if ( m_policy == null )
        {
            setPolicy( POLICY_STATIC );
        }
        else if ( !POLICY_VALID.contains( m_policy ) )
        {
            throw componentMetadata.validationFailure( "Policy must be one of " + POLICY_VALID );
        }

        if ( m_policy_option == null )
        {
            setPolicyOption( POLICY_OPTION_RELUCTANT );
        }
        else if ( !POLICY_OPTION_VALID.contains( m_policy_option ) )
        {
            throw componentMetadata.validationFailure( "Policy option must be one of " + POLICY_OPTION_VALID );
        }
        else if ( !dsVersion.isDS12() && !POLICY_OPTION_RELUCTANT.equals( m_policy_option ) )
        {
            throw componentMetadata.validationFailure( "Policy option must be reluctant for DS < 1.2" );
        }

        if (m_scopeName != null) {
        	    if ( !dsVersion.isDS13() )
        	    {
        		    throw componentMetadata.validationFailure( "reference scope can be set only for DS >= 1.3");
        	    }
            	try
        	    {
        		    m_scope = ReferenceScope.valueOf(m_scopeName);
        	    }
        	    catch (final IllegalArgumentException e)
        	    {
        		    throw componentMetadata.validationFailure( "reference scope must be 'bundle' or 'prototype' not " + m_scopeName);
        	    }
        }

        // checks for event based injection
        // updated method is only supported in namespace xxx and later
        if ( m_updated != null && !(dsVersion.isDS12() || dsVersion == DSVersion.DS11Felix) )
        {
            // FELIX-3648 validation must fail (instead of just ignore)
            throw componentMetadata.validationFailure( "updated method declaration requires DS 1.2 or later namespace " );
        }

        // checks for field injection
        if ( m_field != null )
        {
            // field reference requires DS 1.3
            if ( !dsVersion.isDS13() )
            {
                throw componentMetadata.validationFailure( "Field reference requires DS >= 1.3" );
            }

            // field strategy
            if ( m_field_option == null )
            {
                setFieldOption( FIELD_STRATEGY_REPLACE );
            }
            else if ( !FIELD_STRATEGY_VALID.contains( m_field_option ) )
            {
                throw componentMetadata.validationFailure( "Field strategy must be one of " + FIELD_STRATEGY_VALID );
            }
            if ( !m_isMultiple )
            {
                // update is not allowed for unary references
                if ( m_field_option.equals(FIELD_STRATEGY_UPDATE) )
                {
                    throw componentMetadata.validationFailure( "Field strategy update not allowed for unary field references." );
                }
            }

            // field value type
            if (m_collection_type != null
                && !FIELD_VALUE_TYPE_VALID.contains(m_collection_type))
            {
                throw componentMetadata.validationFailure(
                    "Field value type must be one of " + FIELD_VALUE_TYPE_VALID);
            }
        }

        if ( m_parameter != null )
        {
            // parameter requires DS 1.4
            if ( !dsVersion.isDS14() )
            {
                throw componentMetadata.validationFailure( "Reference parameter requires DS >= 1.4" );
            }
            try
            {
              	m_parameterIndex = Integer.valueOf(m_parameter);
            }
            catch ( final NumberFormatException nfe)
            {
                throw componentMetadata.validationFailure( "Reference parameter is not a number: " + m_parameter );
            }
            if ( m_parameterIndex < 0 )
            {
                throw componentMetadata.validationFailure( "Reference parameter value must be zero or higher: " + m_parameter );
            }
            // parameter value type
            if (m_collection_type == null)
            {
                setCollectionType(FIELD_VALUE_TYPE_SERVICE);
            }
            else if (!FIELD_VALUE_TYPE_VALID.contains(m_collection_type))
            {
                throw componentMetadata.validationFailure(
                    "Collection value type must be one of " + FIELD_VALUE_TYPE_VALID);
            }
        }
        m_validated = true;
    }

    public String getDebugInfo()
    {
        return getName() +
                "interface=" + this.getInterface() +
                ", filter=" + this.getTarget() +
                ", policy=" + this.getPolicy() +
                ", cardinality=" + this.getCardinality() +
                ", bind=" + this.getBind() +
                ", unbind=" + this.getUnbind() +
                ", updated=" + this.getUpdated() +
                ", field=" + this.getField() +
                ", field-option=" + this.getFieldOption() +
                ", collection-type=" + this.getCollectionType()
            +
                ", parameter=" + this.getParameterIndex();
    }

    void collectStrings(Set<String> strings)
    {
        addString(m_bind, strings);
        addString(m_cardinality, strings);
        addString(m_collection_type, strings);
        addString(m_field, strings);
        addString(m_field_option, strings);
        addString(m_interface, strings);
        addString(m_name, strings);
        addString(m_parameter, strings);
        addString(m_policy, strings);
        addString(m_policy_option, strings);
        addString(m_scopeName, strings);
        addString(m_scope.toString(), strings);
        addString(m_target, strings);
        addString(m_unbind, strings);
        addString(m_updated, strings);
    }

    void store(DataOutputStream out, MetaDataWriter metaDataWriter) throws IOException
    {
        metaDataWriter.writeString(m_bind, out);
        metaDataWriter.writeString(m_cardinality, out);
        metaDataWriter.writeString(m_collection_type, out);
        metaDataWriter.writeString(m_field, out);
        metaDataWriter.writeString(m_field_option, out);
        metaDataWriter.writeString(m_interface, out);
        out.writeBoolean(m_isMultiple);
        out.writeBoolean(m_isOptional);
        out.writeBoolean(m_isReluctant);
        out.writeBoolean(m_isReplace);
        out.writeBoolean(m_isStatic);
        metaDataWriter.writeString(m_name, out);
        metaDataWriter.writeString(m_parameter, out);
        out.writeBoolean(m_parameterIndex != null);
        if (m_parameterIndex != null)
        {
            out.writeInt(m_parameterIndex.intValue());
        }
        metaDataWriter.writeString(m_policy, out);
        metaDataWriter.writeString(m_policy_option, out);
        metaDataWriter.writeString(m_scopeName, out);
        metaDataWriter.writeString(m_scope.toString(), out);
        metaDataWriter.writeString(m_target, out);
        metaDataWriter.writeString(m_unbind, out);
        metaDataWriter.writeString(m_updated, out);
    }

    static ReferenceMetadata load(DataInputStream in, MetaDataReader metaDataReader)
        throws IOException
    {
        ReferenceMetadata result = new ReferenceMetadata();
        result.m_bind = metaDataReader.readString(in);
        result.m_cardinality = metaDataReader.readString(in);
        result.m_collection_type = metaDataReader.readString(in);
        result.m_field = metaDataReader.readString(in);
        result.m_field_option = metaDataReader.readString(in);
        result.m_interface = metaDataReader.readString(in);
        result.m_isMultiple = in.readBoolean();
        result.m_isOptional = in.readBoolean();
        result.m_isReluctant = in.readBoolean();
        result.m_isReplace = in.readBoolean();
        result.m_isStatic = in.readBoolean();
        result.m_name = metaDataReader.readString(in);
        result.m_parameter = metaDataReader.readString(in);
        if (in.readBoolean())
        {
            result.m_parameterIndex = Integer.valueOf(in.readInt());
        }
        result.m_policy = metaDataReader.readString(in);
        result.m_policy_option = metaDataReader.readString(in);
        result.m_scopeName = metaDataReader.readString(in);
        result.m_scope = ReferenceScope.valueOf(metaDataReader.readString(in));
        result.m_target = metaDataReader.readString(in);
        result.m_unbind = metaDataReader.readString(in);
        result.m_updated = metaDataReader.readString(in);

        // only stored valid metadata
        result.m_validated = true;
        return result;
    }
}