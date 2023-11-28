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

package org.apache.felix.ipojo.manipulation;

import org.apache.felix.ipojo.manipulator.metadata.annotation.visitor.util.Names;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.Set;


/**
 * Constructor Adapter.
 * This class adds an instance manager argument (so switch variable index).
 * Moreover, it adapts field accesses to delegate accesses to the instance
 * manager if needed.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ConstructorCodeAdapter extends GeneratorAdapter implements Opcodes {

    /**
     * The class containing the field.
     * m_owner : String
     */
    private String m_owner;

    /**
     * Is the super call detected ?
     */
    private boolean m_superDetected;

    /**
     * The super class.
     */
    private String m_superClass;

    /**
     * Set of contained fields.
     */
    private Set<String> m_fields;

    /**
     * Set of contained final fields
     */
    private Set<String> m_finalFields;


    /**
     * PropertyCodeAdapter constructor.
     * A new FiledCodeAdapter should be create for each method visit.
     *
     * @param mv          the MethodVisitor
     * @param owner       the name of the class
     * @param fields      the list of contained fields
     * @param finalFields the list of contained final fields
     * @param access      the constructor access
     * @param name        the name
     * @param desc        the constructor descriptor
     */
    public ConstructorCodeAdapter(final MethodVisitor mv, final String owner, Set<String> fields, Set<String> finalFields, int access, String name, String desc, String superClass) {
        super(Opcodes.ASM9, mv, access, name, desc);
        m_owner = owner;
        m_superDetected = false;
        m_fields = fields;
        m_finalFields = finalFields;
        m_superClass = superClass;
    }

    /**
     * Visits an annotation.
     * If the annotation is visible, the annotation is removed. In fact
     * the annotation was already moved to the method replacing this one.
     * If the annotation is not visible, this annotation is kept on this method.
     *
     * @param name    the name of the annotation
     * @param visible the annotation visibility
     * @return the <code>null</code> if the annotation is visible, otherwise returns
     * {@link GeneratorAdapter#visitAnnotation(String, boolean)}
     * @see org.objectweb.asm.commons.GeneratorAdapter#visitAnnotation(java.lang.String, boolean)
     */
    public AnnotationVisitor visitAnnotation(String name, boolean visible) {
        // Annotations are moved to the injected constructor.
        if (visible) {
            return null;
        } else {
            return super.visitAnnotation(name, false);
        }
    }

    /**
     * Visits a parameter annotation.
     * Parameter annotations are moved to replacing constructor except
     * they are injection annotations(-@Property and -@Requires).
     * Because injection annotations shouldn't be copied to generated one
     * in case of re-manipulation, since this is caused to wrong type resolution
     * of injected parameters.
     *
     * @param parameter parameter index
     * @param desc      annotation description(annotation name)
     * @param visible   is parameter annotation visible
     * @return @AnnotationVisitor
     */
    public AnnotationVisitor visitParameterAnnotation(
            final int parameter,
            final String desc,
            final boolean visible) {

        /*
         * Generated constructor shouldn't inherit injection annotations
         */
        if (desc.equals("Lorg/apache/felix/ipojo/annotations/Property;")
                || desc.equals("Lorg/apache/felix/ipojo/annotations/Requires;")
                || Names.isCustomAnnotation(desc)) {
            return null;
        } else {
            return super.visitParameterAnnotation(parameter, desc, visible);
        }

    }


    /**
     * Adapts field accesses.
     * If the field is owned by the visited class:
     * <ul>
     * <li><code>GETFIELD</code> are changed to a <code>__getX</code> invocation.</li>
     * <li><code>SETFIELD</code> are changed to a <code>__setX</code> invocation.</li>
     * </ul>
     *
     * @param opcode the visited operation code
     * @param owner  the owner of the field
     * @param name   the name of the field
     * @param desc   the descriptor of the field
     * @see org.objectweb.asm.MethodVisitor#visitFieldInsn(int, String, String, String)
     */
    public void visitFieldInsn(
            final int opcode,
            final String owner,
            final String name,
            final String desc) {
        if (m_fields.contains(name) && m_owner.equals(owner)) {
            if (opcode == GETFIELD) {
                String gDesc = "()" + desc;
                mv.visitMethodInsn(INVOKEVIRTUAL, owner, "__get" + name, gDesc, false);
                return;
            } else if (opcode == PUTFIELD) {
                if (m_finalFields.contains(name)) {
                    mv.visitFieldInsn(PUTFIELD, owner, name, desc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, owner, name, desc);
                }
                String sDesc = "(" + desc + ")V";
                mv.visitMethodInsn(INVOKEVIRTUAL, owner, "__set" + name, sDesc, false);
                return;
            }
        }
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    /**
     * Visits a method invocation instruction.
     * After the super constructor invocation, insert the _setComponentManager invocation.
     * Otherwise, the method invocation doesn't change
     *
     * @param opcode the opcode
     * @param owner  the class owning the invoked method
     * @param name   the method name
     * @param desc   the method descriptor
     * @param itf    if the method's owner class is an interface
     * @see org.objectweb.asm.commons.GeneratorAdapter#visitMethodInsn(int, java.lang.String, java.lang.String, java.lang.String)
     */
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {

        // A method call is detected, check if it is the super call :
        // the first init is not necessary the super call, so check that it is really the super class.
        if (!m_superDetected && name.equals("<init>") && owner.equals(m_superClass)) {
            m_superDetected = true;
            // The first invocation is the super call
            // 1) Visit the super constructor :

            //mv.visitVarInsn(ALOAD, 0); The ALOAD 0 was already visited. This previous visit allows
            // Super constructor parameters.
            mv.visitMethodInsn(opcode, owner, name, desc, false); // Super constructor invocation

            // 2) Load the object and the component manager argument
            mv.visitVarInsn(ALOAD, 0);
            //mv.visitVarInsn(ALOAD, Type.getArgumentTypes(m_constructorDesc).length);
            mv.visitVarInsn(ALOAD, 1);  // CM is always the first argument
            // 3) Initialize the field
            mv.visitMethodInsn(INVOKESPECIAL, m_owner, "_setInstanceManager",
                    "(Lorg/apache/felix/ipojo/InstanceManager;)V", false);

        } else {
            if (opcode == INVOKEINTERFACE || (opcode == INVOKESTATIC && itf)) {
                mv.visitMethodInsn(opcode, owner, name, desc, true);
            } else {
                mv.visitMethodInsn(opcode, owner, name, desc, false);
            }
        }
    }

    /**
     * Visits a variable instruction.
     * This method increments the variable index if
     * it is not <code>this</code> (i.e. 0). This increment
     * is due to the instance manager parameter added in the method
     * signature.
     *
     * @param opcode the opcode
     * @param var    the variable index
     * @see org.objectweb.asm.commons.GeneratorAdapter#visitVarInsn(int, int)
     */
    public void visitVarInsn(int opcode, int var) {
        if (var == 0) {
            mv.visitVarInsn(opcode, var); // ALOAD 0 (THIS)
        } else {
            mv.visitVarInsn(opcode, var + 1); // All other variable index must be incremented (due to
            // the instance manager argument
        }

    }

    /**
     * Visits an increment instruction.
     * This method increments the variable index if
     * it is not <code>this</code> (i.e. 0). This increment
     * is due to the instance manager parameter added in the method
     * signature.
     *
     * @param var       the variable index
     * @param increment the increment
     * @see org.objectweb.asm.commons.GeneratorAdapter#visitIincInsn(int, int)
     */
    public void visitIincInsn(int var, int increment) {
        if (var != 0) {
            mv.visitIincInsn(var + 1, increment);
        } else {
            mv.visitIincInsn(var, increment); // Increment the current object ???
        }
    }

    /**
     * Visits a local variable.
     * Adds _manager and increment others variable indexes.
     * This variable has the same scope than <code>this</code> and
     * has the <code>1</code> index.
     *
     * @param name      the variable name
     * @param desc      the variable descriptor
     * @param signature the variable signature
     * @param start     the beginning label
     * @param end       the ending label
     * @param index     the variable index
     * @see org.objectweb.asm.commons.GeneratorAdapter#visitLocalVariable(java.lang.String, java.lang.String, java.lang.String, org.objectweb.asm.Label, org.objectweb.asm.Label, int)
     */
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        if (index == 0) {
            mv.visitLocalVariable(name, desc, signature, start, end, index);
            mv.visitLocalVariable("_manager", "Lorg/apache/felix/ipojo/InstanceManager;", null, start, end, 1);
        } else {
            mv.visitLocalVariable(name, desc, signature, start, end, index + 1);
        }
    }

    /**
     * Visit max method.
     * The stack size is incremented of 1. The
     * local variable count is incremented of 2.
     *
     * @param maxStack  the stack size.
     * @param maxLocals the local variable count.
     * @see org.objectweb.asm.commons.GeneratorAdapter#visitMaxs(int, int)
     */
    public void visitMaxs(int maxStack, int maxLocals) {
        mv.visitMaxs(maxStack + 1, maxLocals + 2);
    }

}
