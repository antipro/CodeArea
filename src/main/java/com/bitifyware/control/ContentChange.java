package com.bitifyware.control;

/** A completed edit notification without old/new document Strings. UTF-16 offsets. */
public record ContentChange(long version, int start, int removedLength, int insertedLength) { }
