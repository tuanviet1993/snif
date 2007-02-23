// Physical Layer
cc.freq = 868000000;
cc.sop = 0x55aa;
cc.crc = 0xF1F1;

// endianess
defaults.endianness = "little";

// basic message type
defaults.packet =    "TOS_Msg";

// Types
typedef uint16_t      mote_id_t;
typedef uint16_t      seqno_t;
typedef uint8_t       quality_t;

// Constants
const int MULTIHOP_LINKESTIMATORBEACON        = 1;
const int MULTIHOP_LINKESTIMATORADVERTISEMENT = 2;
const int MULTIHOP_PATHADVERTISEMENT          = 3;
const int MULTIHOP_MULTIHOPPACKET             = 4;

const int MULTIHOP_MH_TIMESYNC                = 4;
const int MULTIHOP_MH_DSE                     = 5;
const int MULTIHOP_MH_SYMPATHY                = 6;

struct TOS_Msg
{
    uint16_t addr; 
    uint8_t type; 
    uint8_t group; 
    uint8_t length; 
    int8_t data[ length ]; 
    uint16_t crc; 
};

struct LinkEstimatorBeacon : TOS_Msg.data ( type == MULTIHOP_LINKESTIMATORBEACON )
{
	mote_id_t id;
	seqno_t   seqNr;
};

struct link_quality_t
{
	mote_id_t id;
	quality_t quality;
};

struct LinkAdvertisement : TOS_Msg.data ( type == MULTIHOP_LINKESTIMATORADVERTISEMENT )
{
	mote_id_t id;
	struct link_quality_t links[]; // variable size, can be calculated from TOS_Msg.length	
};

struct path_quality_t
{
	mote_id_t sink;
 	quality_t quality;
 	uint8_t round;  // acts as time stamp, new rounds are started by sink only
};

struct PathAdvertisement : TOS_Msg.data ( type == MULTIHOP_PATHADVERTISEMENT )
{
	mote_id_t id;
	struct path_quality_t paths[];
};

struct MultiHopPacket : TOS_Msg.data ( type == MULTIHOP_MULTIHOPPACKET )
{
	mote_id_t src;
	mote_id_t dst;
	seqno_t seqno;
	uint8_t type;
	// more data		
};

