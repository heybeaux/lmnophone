// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458). Builds an m-send-req with the vendored AOSP mmslib subset.
package org.plainphone.spike458;

import android.content.Context;
import org.plainphone.spike458.mmslib.InvalidHeaderValueException;
import org.plainphone.spike458.mmslib.pdu.EncodedStringValue;
import org.plainphone.spike458.mmslib.pdu.PduBody;
import org.plainphone.spike458.mmslib.pdu.PduComposer;
import org.plainphone.spike458.mmslib.pdu.PduHeaders;
import org.plainphone.spike458.mmslib.pdu.PduPart;
import org.plainphone.spike458.mmslib.pdu.SendReq;

final class MmsLibSend {
    private MmsLibSend() {}

    /** One audio part, multipart/mixed, no SMIL: the same shape as the hand-written AMR encoder. */
    static byte[] sendReq(Context context, String to, String partType, String partName, byte[] data) {
        SpikeSafety.requireFakeRecipient(to);
        SendReq req = new SendReq();
        req.setContentType("application/vnd.wap.multipart.mixed".getBytes());
        req.setTo(EncodedStringValue.encodeStrings(new String[] {to}));
        req.setDate(System.currentTimeMillis() / 1000L);
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        try {
            req.setPriority(PduHeaders.PRIORITY_NORMAL);
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
            throw new IllegalStateException("mmslib rejected a fixed header value", e);
        }
        PduPart part = new PduPart();
        part.setContentType(partType.getBytes());
        part.setName(partName.getBytes());
        part.setContentLocation(partName.getBytes());
        part.setContentId(("<" + partName + ">").getBytes());
        part.setData(data);
        PduBody body = new PduBody();
        body.addPart(part);
        req.setBody(body);
        req.setMessageSize(data.length);
        byte[] pdu = new PduComposer(context, req).make();
        if (pdu == null) throw new IllegalStateException("mmslib PduComposer returned null");
        return pdu;
    }
}
