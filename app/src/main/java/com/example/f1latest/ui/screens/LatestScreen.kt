package com.example.f1latest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.f1latest.MainViewModel
import com.example.f1latest.OpenF1UiState
import com.example.f1latest.Session
import com.example.f1latest.Position
import com.example.f1latest.OpenF1Driver
import com.example.f1latest.Race
import com.example.f1latest.Result

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("F1 Latest Session", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFF1801))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.fetchLatestSessionData() },
                containerColor = Color(0xFFFF1801)
            ) {
                Text("Refresh", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is OpenF1UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is OpenF1UiState.Error -> {
                    Text(
                        text = state.message,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                is OpenF1UiState.Fallback -> {
                    val race = state.race
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.requiresAuth) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "A live F1 session is currently in progress!",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100),
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "OpenF1 restricts live telemetry to paid API key holders. Showing the last completed race instead.",
                                        color = Color.DarkGray,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { apiKeyInput = it },
                                        label = { Text("OpenF1 API Key (Optional)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.saveApiKey(apiKeyInput) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1801)),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Save & Retry")
                                    }
                                }
                            }
                        }
                        
                        RaceHeader(race)
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(race.results ?: emptyList()) { result ->
                                ResultRow(result)
                            }
                        }
                    }
                }
                is OpenF1UiState.Success -> {
                    val session = state.session
                    val positions = state.positions
                    val drivers = state.drivers
                    
                    if (session != null) {
                        Column {
                            SessionHeader(session)
                            
                            val latestPositions = positions
                                .groupBy { it.driverNumber }
                                .mapValues { it.value.maxByOrNull { pos -> pos.date } }
                                .values
                                .filterNotNull()
                                .sortedBy { it.position }
                            
                            LazyColumn {
                                items(latestPositions) { position ->
                                    val driver = drivers.find { it.driverNumber == position.driverNumber }
                                    PositionRow(position, driver)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No active session found.",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionHeader(session: Session) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEEEEEE))
            .padding(16.dp)
    ) {
        Text(
            text = "${session.sessionType} - ${session.circuitName}",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = session.sessionName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PositionRow(position: Position, driver: OpenF1Driver?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "P${position.position}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driver?.fullName ?: "Driver ${position.driverNumber}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = driver?.teamName ?: "Unknown Team",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun RaceHeader(race: Race) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEEEEEE))
            .padding(16.dp)
    ) {
        Text(
            text = "Season ${race.season} - Round ${race.round}",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = race.raceName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = race.circuit.circuitName,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ResultRow(result: Result) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.position,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${result.driver.givenName} ${result.driver.familyName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = result.constructor.name,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${result.points} pts",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF1801)
                )
                Text(
                    text = result.time?.time ?: result.status,
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}
