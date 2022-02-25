// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import static com.yahoo.config.model.test.TestUtil.joinLines;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

/**
 * @author arnej
 */
public class IntermediateCollectionTestCase {

    @Test
    public void can_add_minimal_schema() throws Exception {
        String input = joinLines
            ("schema foo {",
             "  document bar {",
             "  }",
             "}");
        var collection = new IntermediateCollection();
        ParsedSchema schema = collection.addSchemaFromString(input);
        assertEquals("foo", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("bar", schema.getDocument().name());
    }

    @Test
    public void can_add_schema_files() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/derived/deriver/child.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/grandparent.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/parent.sd");
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 3);
        var schema = schemes.get("child");
        assertTrue(schema != null);
        assertEquals(schema.name(), "child");
        schema = schemes.get("parent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "parent");
        schema = schemes.get("grandparent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "grandparent");
    }

    NamedReader readerOf(String fileName) throws Exception {
        File f = new File(fileName);
        FileReader fr = new FileReader(f, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(fr);
        return new NamedReader(fileName, br);
    }

    @Test
    public void can_add_schemas() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromReader(readerOf("src/test/derived/deriver/child.sd"));
        collection.addSchemaFromReader(readerOf("src/test/derived/deriver/grandparent.sd"));
        collection.addSchemaFromReader(readerOf("src/test/derived/deriver/parent.sd"));
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 3);
        var schema = schemes.get("child");
        assertTrue(schema != null);
        assertEquals(schema.name(), "child");
        schema = schemes.get("parent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "parent");
        schema = schemes.get("grandparent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "grandparent");
    }

    @Test
    public void can_add_extra_rank_profiles() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/derived/rankprofilemodularity/test.sd");
        collection.addRankProfileFile("test", "src/test/derived/rankprofilemodularity/test/outside_schema1.profile");
        collection.addRankProfileFile("test", readerOf("src/test/derived/rankprofilemodularity/test/outside_schema2.profile"));
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 1);
        var schema = schemes.get("test");
        assertTrue(schema != null);
        assertEquals(schema.name(), "test");
        var rankProfiles = schema.getRankProfiles();
        assertEquals(rankProfiles.size(), 7);
        var outside = rankProfiles.get("outside_schema1");
        assertTrue(outside != null);
        assertEquals(outside.name(), "outside_schema1");
        var functions = outside.getFunctions();
        assertEquals(functions.size(), 1);
        assertEquals(functions.get(0).name(), "fo1");
        outside = rankProfiles.get("outside_schema2");
        assertTrue(outside != null);
        assertEquals(outside.name(), "outside_schema2");
        functions = outside.getFunctions();
        assertEquals(functions.size(), 1);
        assertEquals(functions.get(0).name(), "fo2");
    }

}
