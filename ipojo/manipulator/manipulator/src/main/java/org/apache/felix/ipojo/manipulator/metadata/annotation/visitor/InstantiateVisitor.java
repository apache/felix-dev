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

package org.apache.felix.ipojo.manipulator.metadata.annotation.visitor;

import org.apache.felix.ipojo.manipulator.metadata.annotation.ComponentWorkbench;
import org.apache.felix.ipojo.metadata.Attribute;
import org.apache.felix.ipojo.metadata.Element;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Parse the @Instantitate annotation.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 * @see org.apache.felix.ipojo.annotations.Instantiate
 */
public class InstantiateVisitor extends AnnotationVisitor {

    private Element instance = new Element("instance", "");

    private ComponentWorkbench workbench;

    public InstantiateVisitor(ComponentWorkbench workbench) {
        super(Opcodes.ASM9);
        this.workbench = workbench;
    }

    /**
     * Visit an annotation attribute.
     *
     * @param name  the attribute name
     * @param value the attribute value
     * @see org.objectweb.asm.AnnotationVisitor#visit(java.lang.String, java.lang.Object)
     */
    public void visit(String name, Object value) {
        if (name.equals("name")) {
            instance.addAttribute(new Attribute("name", (String) value));
        }
    }

    /**
     * End of the visit. Creates the instance element.
     *
     * @see org.objectweb.asm.AnnotationVisitor#visitEnd()
     */
    public void visitEnd() {
        // We set the instance's component attribute to the class name, if the component type has a custom name,
        // we will update it.
        instance.addAttribute(new Attribute("component", workbench.getType().getClassName()));

        workbench.setInstance(instance);

    }
}
