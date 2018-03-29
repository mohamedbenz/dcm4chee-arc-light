/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.rs;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.export.mgt.ExportBatch;
import org.dcm4chee.arc.export.mgt.ExportBatchOrder;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.query.util.MatchBatch;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */
@RequestScoped
@Path("monitor/export/batch")
public class ExportBatchRS {
    private static final Logger LOG = LoggerFactory.getLogger(ExportBatchRS.class);

    @Inject
    private ExportManager mgr;

    @Inject
    private Device device;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("ExporterID")
    private String exporterID;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("orderby")
    @Pattern(regexp = "(-?)createdTime|(-?)updatedTime")
    private String orderby;

    @Context
    private HttpServletRequest request;

    @GET
    @NoCache
    public Response listExportBatches() {
        logRequest();
        List<ExportBatch> exportBatches = mgr.listExportBatches(
                MatchBatch.matchQueueBatch(deviceName, status()),
                MatchBatch.matchExportBatch(exporterID, deviceName, createdTime, updatedTime),
                order(orderby), parseInt(offset), parseInt(limit));
        return Response.ok().entity(Output.JSON.entity(exportBatches)).build();
    }

    private enum Output {
        JSON {
            @Override
            Object entity(final List<ExportBatch> exportBatches) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (ExportBatch exportBatch : exportBatches) {
                        JsonWriter writer = new JsonWriter(gen);
                        gen.writeStartObject();
                        writer.writeNotNullOrDef("batchID", exportBatch.getBatchID(), null);
                        writeTasks(exportBatch, writer);
                        writer.writeNotEmpty("dicomDeviceName", exportBatch.getDeviceNames());
                        writer.writeNotEmpty("ExporterID", exportBatch.getExporterIDs());
                        writer.writeNotEmpty("createdTimeRange", exportBatch.getCreatedTimeRange());
                        writer.writeNotEmpty("updatedTimeRange", exportBatch.getUpdatedTimeRange());
                        writer.writeNotEmpty("scheduledTimeRange", exportBatch.getScheduledTimeRange());
                        writer.writeNotEmpty("processingStartTimeRange", exportBatch.getProcessingStartTimeRange());
                        writer.writeNotEmpty("processingEndTimeRange", exportBatch.getProcessingEndTimeRange());
                        gen.writeEnd();
                    }
                    gen.writeEnd();
                    gen.flush();
                };
            }

            private void writeTasks(ExportBatch exportBatch, JsonWriter writer) {
                writer.writeStartObject("tasks");
                writer.writeNotNullOrDef("scheduled", exportBatch.getScheduled(), 0);
                writer.writeNotNullOrDef("in-process", exportBatch.getInProcess(), 0);
                writer.writeNotNullOrDef("warning", exportBatch.getWarning(), 0);
                writer.writeNotNullOrDef("failed", exportBatch.getFailed(), 0);
                writer.writeNotNullOrDef("canceled", exportBatch.getCanceled(), 0);
                writer.writeNotNullOrDef("completed", exportBatch.getCompleted(), 0);
                writer.writeEnd();
            }
        };

        abstract Object entity(final List<ExportBatch> exportBatches);
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private static ExportBatchOrder order(String orderby) {
        return orderby != null
                ? ExportBatchOrder.valueOf(orderby.replace('-', '_'))
                : ExportBatchOrder._updatedTime;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }
}
