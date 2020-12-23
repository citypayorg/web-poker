package ar.com.tandilweb.room.clientControllers;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.com.tandilweb.exchange.backwardValidation.ChallengeValidation;
import ar.com.tandilweb.exchange.clientOperations.Deposit;
import ar.com.tandilweb.exchange.gameProtocol.texasHoldem.accessing.SelectPosition;
import ar.com.tandilweb.exchange.userAuth.ActiveSession;
import ar.com.tandilweb.exchange.userAuth.Authorization;
import ar.com.tandilweb.exchange.userAuth.BackwardValidation;
import ar.com.tandilweb.exchange.userAuth.Challenge;
import ar.com.tandilweb.exchange.userAuth.types.ChallengeActions;
import ar.com.tandilweb.room.handlers.GameHandler;
import ar.com.tandilweb.room.handlers.RoomHandler;
import ar.com.tandilweb.room.handlers.SessionHandler;
import ar.com.tandilweb.room.orchestratorBridge.OrchestratorThread;
import ar.com.tandilweb.room_int.handlers.dto.UserData;
import ar.com.tandilweb.room_int.handlers.dto.UserDataStatus;

@Controller
@MessageMapping("/user")
public class AuthController {
	
	private static final Logger log = LoggerFactory.getLogger(AuthController.class);
	
	@Autowired
	private SessionHandler sessionHandler;
	
	@Autowired
	private RoomHandler roomHandler;
	
	@Autowired
	private OrchestratorThread orchestrator;
	
	@Autowired
	private GameHandler gameHandler;
	
	@MessageMapping("/authorization")
	@SendToUser("/AuthController/challenge")
	public Challenge authorization(Authorization auth, SimpMessageHeaderAccessor headerAccessor) {
		String sessID = headerAccessor.getSessionId();
		log.debug("New session: " + sessID);
		// log.debug("auth.userID: " + auth.userID);
		if(sessionHandler.isActiveSessionForUser(auth.userID)) {
			ActiveSession activeSession = new ActiveSession();
			sessionHandler.sendToUserID("/AuthController/activeSession", auth.userID, activeSession);
			// log.debug("게임중sit인 userID: " + auth.userID); // log 추가 
		}
		Challenge challenge = new Challenge();
		challenge.action = ChallengeActions.LOGIN;
		challenge.roomID = roomHandler.getRoomID();
		challenge.claimToken = UUID.randomUUID().toString();
		UserData userData = new UserData();
		userData.lastChallenge = challenge;
		userData.sessID = sessID;
		userData.userID = auth.userID;
		userData.status = UserDataStatus.PENDING;
		sessionHandler.assocSessionWithUserData(sessID, userData);
		//sessionHandler.sendToSessID("/AuthController/challenge", sessID, challenge);
		return challenge;
	}
	
	@MessageMapping("/backwardValidation")
	public void backwardValidation(BackwardValidation bV, SimpMessageHeaderAccessor headerAccessor) {
		String sessID = headerAccessor.getSessionId();
		// send to orchestrator:
		ChallengeValidation challengeValidation = new ChallengeValidation();
		challengeValidation.idChallenge = bV.idChallenge;
		challengeValidation.transactionID = UUID.randomUUID().toString();
		UserData uD = sessionHandler.getUserDataBySession(sessID);
		uD.transactionID = challengeValidation.transactionID;
		uD.challengeAction = bV.action;
		ObjectMapper om = new ObjectMapper();
		try {
			orchestrator.sendDataToServer(om.writeValueAsString(challengeValidation));
//			server response: (catched in Orchestrator Thread class.)
//			eppr/backward-validation::unknown
//			eppr/backward-validation::invalid
//			eppr/backward-validation::dataChallenge
		} catch (JsonProcessingException e) {
			log.error("Error processing challenge validation", e);
		}
	}
	
	@MessageMapping("/selectPosition")
	public void selectPosition(SelectPosition selectedPosition, SimpMessageHeaderAccessor headerAccessor){
		String sessID = headerAccessor.getSessionId();
		gameHandler.sitFlow(selectedPosition.position, sessionHandler.getUserDataBySession(sessID));
	}
	
	@MessageMapping("/deposit")
	@SendToUser("/AuthController/challenge")
	public Challenge deposit(Deposit deposit, SimpMessageHeaderAccessor headerAccessor){
		String sessID = headerAccessor.getSessionId();
		Challenge challenge = new Challenge();
		challenge.action = ChallengeActions.DEPOSIT;
		challenge.roomID = roomHandler.getRoomID();
		challenge.claimToken = UUID.randomUUID().toString();
		UserData uD = sessionHandler.getUserDataBySession(sessID);
		uD.lastChallenge = challenge;
		uD.requestForDeposit = deposit.chips;
		return challenge;
	}
	
	// TODO: make leave schema implementation.
	
	// @MessageMapping("/leaveInRoom")
	// public void leaveInRoom(){
		
	// 	ObjectMapper om = new ObjectMapper();
	// 	try {
	// 		orchestrator.sendDataToServer(om.writeValueAsString(auth.userID));
	// 	} catch (JsonProcessingException e) {
	// 		log.error("Error processing challenge validation", e);
	// 	}
	// }

}
