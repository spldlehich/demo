/*
 * TeltonikaFMTrackParser.cpp
 *
 *  Created on: 23.09.2013
 *      Author: spl
 */

#include "TeltonikaFMTrackParser.h"
#include "logconfig.h"
#include "md5provider.h"
#include "ilib/date.h"
#include "ExeptionsParser.h"
#include <deque>
#include "Factory.h"

namespace {
	bool b = getFactory().registerFactory( "TeltonikaFM", (AbstractParser*)new TeltonikaFMTrackParser() );
}

unsigned short TeltonikaFMTrackParser::checkSum(const std::string& bytes) {
	unsigned char *pData = (unsigned char *)bytes.c_str();
	unsigned int size = bytes.size();
	unsigned short crc16_result = 0x0000;
	for(unsigned int i=0; i<size; i++)
	{
		unsigned short val=0;
		val = (unsigned short) *(pData+i);
		crc16_result ^= val;
		for (unsigned char j = 0; j < 8; j++)
		{
			crc16_result = crc16_result & 0x0001 ? (crc16_result >>1 ) ^ 0xA001: crc16_result >>1 ;
		}
	}
	return crc16_result;
}

void TeltonikaFMTrackParser::parse(trackers::TrackerMessage& _outMessage) {
	if (imei.size() < 1){
		imei.append(vrd.readString(17));
		std::string response;
		response.append(0x01,1);
		writeToServer(response);
	}else {
		trackers::TrackerMessage outMessage;
		int *zero = (int *)vrd.readString(4).c_str();
		if (*zero != 0)
			throw ExceptionParserTrash();
		const char *sizeC = vrd.readString(4).c_str();
		size_t size = 0;
		for (int i = 0;i < 4; ++i){
			size *= 256;
			size += (unsigned char)sizeC[i];
		}
		LogConfig::logger()->debug2(
				"%s tracker: size in package=%d",nameParser.c_str(),size);
		size_t positStart = vrd.currentPosition();
		const unsigned char codecId = vrd.readUChar();
		if (codecId != 0x08) {
			LogConfig::logger()->error("Error CodecId != 0x08, codec Id=%d",codecId);
			throw ExceptionParserTrash();
		}
		outMessage.set_deviceid(getDeviceId(nameParser,imei.substr(2,imei.size()-2)));
		unsigned char numberOfData = vrd.readUChar();
		for(int i = 0; i < numberOfData; i++){
			trackers::Position posInfo;
			ilib::Date now = ilib::Date::now();
			uint64_t time = vrd.readULongIntBE();
			ilib::Date date  = ilib::Date(time / 1000);
			date.setTZ(now.getTZ());
			posInfo.set_captured_timestamp(date.toUnixTime());
			posInfo.set_received_timestamp(ilib::Date::now().toUnixTime() - now.getTZ() * 3600);

			LogConfig::logger()->debug2("time = %d",posInfo.captured_timestamp());
			unsigned char prior = vrd.readUChar(); // prioriry
			LogConfig::logger()->debug2("prioriry = %d",prior);
			int longitude = vrd.readIntBE();
			LogConfig::logger()->debug2("longitude = %d",longitude);
			int latitude = vrd.readIntBE();
			LogConfig::logger()->debug2("latitude = %d",latitude);
			posInfo.mutable_gps_navigation()->set_lon_deg( ((double)longitude) / 10000000. );
			posInfo.mutable_gps_navigation()->set_lat_deg( ((double)latitude) / 10000000. );
			vrd.readUShortBE(); // Altitude
			unsigned short angle = vrd.readUShortBE(); // Angle
			posInfo.mutable_gps_navigation()->set_direction_deg( (float) angle);
			unsigned char rank = vrd.readUChar();
			posInfo.mutable_gps_navigation()->set_satellite_count(rank);
			unsigned short speed = vrd.readUShortBE(); // speed
			posInfo.mutable_gps_navigation()->set_speed_kmh((float)speed);
			LogConfig::logger()->debug2("IO Elements block");
			// IO Elements block
			vrd.readUChar(); // Event
			vrd.readUChar(); // count IO
			for (int i = 1; i <=8; i*=2){
				unsigned char countElements = vrd.readUChar(); // count elements;
				for (int j = 0; j < countElements; j++){
					unsigned char id= vrd.readUChar(); // IOElement
					uint32_t value = 0;
					switch(i){
					case 1:
						value = (int)vrd.readChar();
						break;
					case 2:
						value = (int)vrd.readShortBE();
						break;
					case 4:
						value = (int)vrd.readIntBE();
						break;
					case 8:
						vrd.skip(i);
						break;
					}
					parseIO(posInfo, id, value);
				}
			}

			LogConfig::logger()->debug2("lat=%f,lon=%f,speed=%f",
					posInfo.mutable_gps_navigation()->lat_deg(),
					posInfo.mutable_gps_navigation()->lon_deg(),
					posInfo.mutable_gps_navigation()->speed_kmh());
			outMessage.add_position()->CopyFrom(posInfo);
		}
		unsigned char numberOfData2 = vrd.readUChar();
		if (numberOfData != numberOfData2) {
			LogConfig::logger()->debug("numberOfData start != numberOfData end");
			throw ExceptionParserTrash();
		}
		const uint32_t calcCrc = checkSum(vrd.readPackage(positStart));
		const uint32_t crc = vrd.readUIntBE();
		if (crc == calcCrc){
			_outMessage.CopyFrom(outMessage);
			LogConfig::logger()->debug2("Position added, crc OK, position size=%d",_outMessage.position_size());

		} else {
			LogConfig::logger()->debug2("Position not added,  crc = %x , crcCalculated = %x",crc,calcCrc);
		}

		std::string response;
		char resByte[4];
		resByte[0] = 0x00;
		resByte[1] = 0x00;
		resByte[2] = 0x00;
		resByte[3] = numberOfData;
		response.append(resByte,4);
		writeToServer(response);
		LogConfig::logger()->debug2("END %s PACKAGE",nameParser.c_str());
	}
}

void TeltonikaFMTrackParser::parseIO(trackers::Position& position, const unsigned char &id, const int32_t value) {
	LogConfig::logger()->debug2("IO Elements block id=%d value=%d",id,value);
	trackers::InputInfo info;
	switch (id){
	case 1:
	case 2:
	case 3:
	case 4:
		info.set_input_id(id);
		info.set_value_uint32(value);
		position.add_digital_input()->CopyFrom(info);
		break;
	case 9:
	case 10:
	case 11:
		info.set_input_id(id - 8);
		info.set_value_uint32(value);
		position.add_analog_input()->CopyFrom(info);
		break;
	case 19:
		info.set_input_id(4);
		info.set_value_uint32(value);
		position.add_analog_input()->CopyFrom(info);
		break;
	case 21:
		if (value > 0)
			position.mutable_gsm_info()->set_signal_strength(35 + (value-1)*19); // convert to dbm
			break;
	case 66:
		position.mutable_device_info()->set_external_power_mv(value);
		break;
	case 67:
		position.mutable_device_info()->set_battery_power_mv(value);
		break;
	case 70:
		if (value != 3000)
			position.mutable_device_info()->set_temperature_c(value / 10);
		break;
	case 182:
		position.mutable_gps_navigation()->set_hdop(value / 10);
		break;
	case 201:
		position.mutable_fms_data()->set_total_fuel_consumed_l(position.mutable_fms_data()->total_fuel_consumed_l() + value);
		break;
	case 203:
		position.mutable_fms_data()->set_total_fuel_consumed_l(position.mutable_fms_data()->total_fuel_consumed_l() + value);
		break;
	case 205:
		position.mutable_gsm_info()->set_cellid(value);
		break;
	case 206:
		position.mutable_gsm_info()->set_lac(value);
		break;
	case 241:
		uint32_t operatorValue = value;
		std::deque<unsigned char> vector;
		for (int i = 0 ; operatorValue > 0; ++i ){
			vector.push_front(operatorValue % 10);
			operatorValue /= 10;
		}
		if (vector.size() > 3){
			uint32_t mcc = 0 , mnc = 0;
			for (size_t i = 0 ; i < 3; ++i)
				mcc = mcc*10 + vector[i];
			for (size_t i = 3 ; i < vector.size(); ++i)
				mnc = mnc*10 + vector[i];
			if ((mcc > 0) && (mnc > 0)){
				position.mutable_gsm_info()->set_mcc(mcc);
				position.mutable_gsm_info()->set_mnc(mnc);
				LogConfig::logger()->debug2("mcc=%d mnc=%d",mcc,mnc);
			}
		}
		break;
	}
}
