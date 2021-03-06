/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.CompilerSettings;
import org.elasticsearch.painless.Definition;
import org.elasticsearch.painless.Definition.Cast;
import org.elasticsearch.painless.Definition.Type;
import org.elasticsearch.painless.AnalyzerCaster;
import org.elasticsearch.painless.Variables;
import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * The superclass for all E* (expression) nodes.
 */
public abstract class AExpression extends ANode {

    /**
     * Set to false when an expression will not be read from such as
     * a basic assignment.  Note this variable is always set by the parent
     * as input.
     */
    protected boolean read = true;

    /**
     * Set to true when an expression can be considered a stand alone
     * statement.  Used to prevent extraneous bytecode. This is always
     * set by the node as output.
     */
    protected boolean statement = false;

    /**
     * Set to the expected type this node needs to be.  Note this variable
     * is always set by the parent as input and should never be read from.
     */
    protected Type expected = null;

    /**
     * Set to the actual type this node is.  Note this variable is always
     * set by the node as output and should only be read from outside of the
     * node itself.  <b>Also, actual can always be read after a cast is
     * called on this node to get the type of the node after the cast.</b>
     */
    protected Type actual = null;

    /**
     * Set by {@link EExplicit} if a cast made on an expression node should be
     * explicit.
     */
    protected boolean explicit = false;

    /**
     * Set to the value of the constant this expression node represents if
     * and only if the node represents a constant.  If this is not null
     * this node will be replaced by an {@link EConstant} during casting
     * if it's not already one.
     */
    protected Object constant = null;

    /**
     * Set to true by {@link ENull} to represent a null value.
     */
    protected boolean isNull = false;

    /**
     * If an expression represents a branch statement, represents the jump should
     * the expression evaluate to a true value.  It should always be the case that only
     * one of tru and fals are non-null or both are null.  Only used during the writing phase.
     */
    protected Label tru = null;

    /**
     * If an expression represents a branch statement, represents the jump should
     * the expression evaluate to a false value.  It should always be the case that only
     * one of tru and fals are non-null or both are null.  Only used during the writing phase.
     */
    protected Label fals = null;

    public AExpression(final int line, final String location) {
        super(line, location);
    }

    /**
     * Checks for errors and collects data for the writing phase.
     */
    abstract void analyze(final CompilerSettings settings, final Definition definition, final Variables variables);

    /**
     * Writes ASM based on the data collected during the analysis phase.
     */
    abstract void write(final CompilerSettings settings, final Definition definition, final GeneratorAdapter adapter);

    /**
     * Inserts {@link ECast} nodes into the tree for implicit casts.  Also replaces
     * nodes with the constant variable set to a non-null value with {@link EConstant}.
     * @return The new child node for the parent node calling this method.
     */
    AExpression cast(final CompilerSettings settings, final Definition definition, final Variables variables) {
        final Cast cast = AnalyzerCaster.getLegalCast(definition, location, actual, expected, explicit);

        if (cast == null) {
            if (constant == null || this instanceof EConstant) {
                return this;
            } else {
                final EConstant econstant = new EConstant(line, location, constant);
                econstant.analyze(settings, definition, variables);

                if (!expected.equals(econstant.actual)) {
                    throw new IllegalStateException(error("Illegal tree structure."));
                }

                return econstant;
            }
        } else {
            if (constant == null) {
                final ECast ecast = new ECast(line, location, this, cast);
                ecast.statement = statement;
                ecast.actual = expected;
                ecast.isNull = isNull;

                return ecast;
            } else {
                if (expected.sort.constant) {
                    constant = AnalyzerCaster.constCast(location, constant, cast);

                    final EConstant econstant = new EConstant(line, location, constant);
                    econstant.analyze(settings, definition, variables);

                    if (!expected.equals(econstant.actual)) {
                        throw new IllegalStateException(error("Illegal tree structure."));
                    }

                    return econstant;
                } else if (this instanceof EConstant) {
                    final ECast ecast = new ECast(line, location, this, cast);
                    ecast.actual = expected;

                    return ecast;
                } else {
                    final EConstant econstant = new EConstant(line, location, constant);
                    econstant.analyze(settings, definition, variables);

                    if (!actual.equals(econstant.actual)) {
                        throw new IllegalStateException(error("Illegal tree structure."));
                    }

                    final ECast ecast = new ECast(line, location, econstant, cast);
                    ecast.actual = expected;

                    return ecast;
                }
            }
        }
    }
}
