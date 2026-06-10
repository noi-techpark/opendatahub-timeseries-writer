// Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
// Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.com)
//
// SPDX-License-Identifier: GPL-3.0-only

package com.opendatahub.timeseries.bdp.dto.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

public class DtoTest {

	private static final String EMPTY_STATION_BRANCH_ID = "emptyStationBranch";
	private static final String TYPE1_ID = "vehicle-speed";
	private static final String STATION_ID = "546asdf";

	@Test
	public void testEmptyDataSet() {
		DataMapDto<RecordDtoImpl> dto = new DataMapDto<RecordDtoImpl>();
		assertTrue(dto.getData().isEmpty());
		DataMapDto<RecordDtoImpl> childMapDto = new DataMapDto<>();
		List<RecordDtoImpl> records = new ArrayList<RecordDtoImpl>();
		records.add(new SimpleRecordDto(121233l,1d));
		childMapDto.setData(records);
		dto.getBranch().put("station1", childMapDto);
		DataMapDto<RecordDtoImpl> childOfChildMapDto = new DataMapDto<>();
		List<RecordDtoImpl> records2 = new ArrayList<>();
		records2.add(new SimpleRecordDto(324l,2d));
		childOfChildMapDto.setData(records2);
		childMapDto.getBranch().put("precipitation", childOfChildMapDto);

		assertFalse(childMapDto.getData().isEmpty());
		assertTrue(dto.getData().isEmpty());
		assertEquals(((SimpleRecordDto)childOfChildMapDto.getData().get(0)).getValue(), Double.valueOf(2));
	}

	@Test
	public void testAddRecords() {
		DataMapDto<RecordDtoImpl> stationMap = new DataMapDto<>();
		stationMap.addRecord(STATION_ID, TYPE1_ID, new SimpleRecordDto(new Date().getTime(), 5.,1));
		DataMapDto<RecordDtoImpl> typeMap = stationMap.getBranch().get(STATION_ID);
		assertNotNull(typeMap);
		assertTrue(typeMap.getData().isEmpty());
		assertFalse(typeMap.getBranch().isEmpty());
		DataMapDto<RecordDtoImpl> dataMap = typeMap.getBranch().get(TYPE1_ID);
		assertNotNull(dataMap);
		assertEquals(1,dataMap.getData().size());
		List<SimpleRecordDto> dtos = new ArrayList<>();
		dtos.add(new SimpleRecordDto(new Date().getTime(),23,200));
		dtos.add(new SimpleRecordDto(new Date().getTime(),1,1));
		stationMap.addRecords(STATION_ID, TYPE1_ID, dtos);
		assertEquals(3, dataMap.getData().size());
	}

	@Test
	public void testCleanMap() {
		DataMapDto<RecordDtoImpl> stationMap = new DataMapDto<>();
		stationMap.addRecord(STATION_ID, TYPE1_ID, new SimpleRecordDto(new Date().getTime(), 5.,1));
		stationMap.getBranch().put(EMPTY_STATION_BRANCH_ID, new DataMapDto<>());
		stationMap.clean();
		assertNull(stationMap.getBranch().get(EMPTY_STATION_BRANCH_ID));
		assertEquals(1, stationMap.getBranch().size());
	}

}
