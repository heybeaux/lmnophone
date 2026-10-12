// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458).
package org.plainphone.spike458;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** A FileProvider stand-in (no androidx): serves files/share/<name> read-only to granted callers. */
public class Files extends ContentProvider {
    static final String AUTHORITY = "org.plainphone.spike458.probe.files";

    static Uri uriFor(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.startsWith(".")) throw new FileNotFoundException(String.valueOf(uri));
        return new File(new File(getContext().getFilesDir(), "share"), name);
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        String n = String.valueOf(uri.getLastPathSegment());
        if (n.endsWith(".amr")) return "audio/amr";
        if (n.endsWith(".pdu")) return "application/vnd.wap.mms-message";
        return "application/octet-stream";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Log.i("Files.openFile uid=" + android.os.Binder.getCallingUid() + " " + uri + " mode=" + mode);
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String sel, String[] args, String order) {
        Log.i("Files.query uid=" + android.os.Binder.getCallingUid() + " " + uri);
        File f;
        try { f = fileFor(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
