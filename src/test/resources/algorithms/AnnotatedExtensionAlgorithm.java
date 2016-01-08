/**
 * ﻿Copyright (C) 2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package algorithms;

import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.algorithm.annotation.LiteralDataOutput;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;

/**
 * Simple mockup algorithm doing nothing special.
 *
 * Compiled with
 *
 * javac -cp "C:\Users\Daniel\.m2\repository\org\n52\wps\52n-wps-algorithm\4.0.0-SNAPSHOT\52n-wps-algorithm-4.0.0-SNAPSHOT.jar;C:\Users\Daniel\.m2\repository\org\n52\wps\52n-wps-commons\4.0.0-SNAPSHOT\52n-wps-commons-4.0.0-SNAPSHOT.jar;C:\Users\Daniel\.m2\repository\org\n52\wps\52n-wps-io\4.0.0-SNAPSHOT\52n-wps-io-4.0.0-SNAPSHOT.jar" AnnotatedExtensionAlgorithm.java
 *
 * @author matthes rieke
 *
 */
@Algorithm(
        version = "0.1",
        abstrakt = "Simple mockup algorithm doing nothing special",
        title = "Simple Algoritm",
        //identifier = "your-identifer",
        statusSupported = false,
        storeSupported = false)
public class AnnotatedExtensionAlgorithm extends AbstractAnnotatedAlgorithm {

    private String output;

    @LiteralDataInput(
            identifier = "input",
            title = "useless input",
            abstrakt = "Whatever you put in, it won't change anything.")
    public String input;

    @LiteralDataOutput(identifier = "output",
            title = "sophisticated output",
            abstrakt = "what will you expect as the output?")
    public String getOutput() {
        return this.output;
    }

    @Execute
    public void myRunMethodFollowingNoSyntaxNoArgumentsAllowed() {
        this.output = "works!";
    }

}
