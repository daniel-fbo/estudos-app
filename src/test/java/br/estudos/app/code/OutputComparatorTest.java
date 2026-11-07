package br.estudos.app.code;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputComparatorTest {

    @Test
    void normalizeHandlesNullAndEmpty() {
        assertEquals(List.of(), OutputComparator.normalize(null));
        assertEquals(List.of(), OutputComparator.normalize(""));
        assertEquals(List.of(), OutputComparator.normalize("   \n\n  \t "));
    }

    @Test
    void normalizeIgnoresTrailingSpacesAndBlankLines() {
        assertEquals(List.of("a", "b"), OutputComparator.normalize("a   \nb \t\n\n\n"));
        assertEquals(List.of("  leading space"), OutputComparator.normalize("  leading space   \n"));
    }

    @Test
    void normalizePreservesInternalEmptyLines() {
        assertEquals(List.of("first", "", "second"), OutputComparator.normalize("first\n\nsecond\n"));
        assertEquals(List.of("first", "", "second"), OutputComparator.normalize("first\n   \nsecond"));
    }

    @Test
    void normalizeHandlesWindowsCrlf() {
        assertEquals(List.of("line1", "line2"), OutputComparator.normalize("line1\r\nline2\r\n"));
        assertTrue(OutputComparator.equal("line1\r\nline2\r\n", "line1\nline2\n"));
    }

    @Test
    void equalDistinguishesLeadingWhitespaceAndInternalLines() {
        assertFalse(OutputComparator.equal("42", " 42"));
        assertFalse(OutputComparator.equal("a\nb", "a\n\nb"));
        assertTrue(OutputComparator.equal("42  \n\n", "42"));
        assertTrue(OutputComparator.equal(null, ""));
    }

    @Test
    void diffReturnsEmptyWhenEqual() {
        assertEquals("", OutputComparator.diff("hello \n", "hello\n\n"));
        assertEquals("", OutputComparator.diff(null, ""));
    }

    @Test
    void diffFormatsLineNumbersAndValues() {
        String diff = OutputComparator.diff("alpha\nbeta\n", "alpha\ngamma\n");
        assertEquals("linha 2: esperado [beta], obtido [gamma]\n", diff);
    }

    @Test
    void diffHandlesDifferingLineCounts() {
        String diffMoreActual = OutputComparator.diff("a\n", "a\nb\n");
        assertEquals("linha 2: esperado (nada), obtido [b]\n", diffMoreActual);

        String diffMoreExpected = OutputComparator.diff("a\nb\n", "a\n");
        assertEquals("linha 2: esperado [b], obtido (nada)\n", diffMoreExpected);
    }
}
