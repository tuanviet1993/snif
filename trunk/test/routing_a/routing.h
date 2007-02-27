typedef struct Neighbor {
	u_short address;
	u_char incomingQuality;
	u_char numberThisRound; // 0 .. 8 
	u_char number;          // to stay in table
	u_char quality;
	u_short parentPathQuality;
} neighbor_t;

