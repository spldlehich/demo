/*
 * TeltonikaFMTrackParser.h
 *
 *  Created on: 23.09.2013
 *      Author: spl
 */

#ifndef TELTONIKATRACKPARSER_H_
#define TELTONIKATRACKPARSER_H_

#include "AbstractParser.h"

class TeltonikaFMTrackParser : public AbstractParser  {
public:
	TeltonikaFMTrackParser(std::string _nameParser = "TELTONIKA"):nameParser(_nameParser){}
	virtual ~TeltonikaFMTrackParser(){}
	virtual void parse(trackers::TrackerMessage &outMessage);
	virtual AbstractParser* createInstance(){
		return new TeltonikaFMTrackParser();
	}
protected:
	std::string nameParser;
private:
	std::string imei;
	unsigned short checkSum(const std::string& bytes);
	void parseIO(trackers::Position& position, const unsigned char &id, const int32_t value);
};

#endif /* TELTONIKATRACKPARSER_H_ */
