<script>
	import {apiUrl, playableQuizzes, playableQuizzesAvailable, animationConfig} from '../stores.js';
	import {onDestroy, createEventDispatcher} from 'svelte';
	import {fade, fly} from 'svelte/transition';
	import {passByVal} from '../util.js'
	import PlayableQuizHint from '../util/PlayableQuizHint.svelte';

	/**
	 * File description:
	 * Provides a component that lets the user edit their quizzes.
	 */

	const dispatch = createEventDispatcher();
	let connectionSuccessful = false;
	let animationConf;
	let apiUrlStore;
	let quizzes = [];
	let editedQuizzes = [];
	let unidenticalQuizzes = [];

	// Generates an array of booleans that express whether a quiz has been changed since it's been fetched from the backend.
	$: unidenticalQuizzes = editedQuizzes.map((_, i) => JSON.stringify(editedQuizzes[i]) !== JSON.stringify(quizzes[i]));

	/**
	 * Initiates every needed resource for proper functioning of the page.
	 *
	 * - Gets quizzes
	 * - Subscribes to stores
	 * - Updates the playable quizzes store.
	 */
	const init = () => {
		const unsubscribeApiUrl = apiUrl.subscribe(value => apiUrlStore = value);
		const unsubscribeAnimationConf = animationConfig.subscribe(value => animationConf = value);
		onDestroy(() => {
			unsubscribeApiUrl();
			unsubscribeAnimationConf();
		});

		fetch(`${apiUrlStore}quiz`, {
			method: 'GET',
			mode: 'cors'
		}).then(res => {
			connectionSuccessful = true;
			return res.json();
		}).then(data => {
			resetAllQuizzes(data);
		}).catch(_ => {
			// Do Nothing.
		});

		fetch(`${apiUrlStore}quiz`, {
			method: 'GET',
			mode: 'cors'
		}).then(res => res.json()).then(data => {
			playableQuizzes.set({
				available: playableQuizzesAvailable(data),
				requestAttempted: true
			});
		}).catch(_ => {
			playableQuizzes.set({
				available: false,
				requestAttempted: true
			});
		});
	};

	init();

	/**
	 * Resets the value of the editedQuizzes and quizzes variable to the data variable contents.
	 *
	 * @param {Object} data The quizzes that overwrite the existing data.
	 */
	function resetAllQuizzes(data) {
		editedQuizzes = passByVal(data);
		quizzes = passByVal(data);
	}

	/**
	 * Resets the value of a single quiz in both the editedQuizzes and quizzes variable.
	 *
	 * @param {Object} data The collection of quizzes from which one should be reset.
	 * @param {string} quizId The id of the quiz that should be reset.
	 */
	function resetSingleQuiz(data, quizId) {
		data.forEach((quiz, i) => {
			if (quiz.id === quizId) {
				editedQuizzes[i] = passByVal(quiz);
				quizzes[i] = passByVal(quiz);
			}
		});
	}

	/**
	 * Creates a new quiz in the database and updates the quizzes.
	 */
	function createQuiz() {
		let data = {
			"title": "",
			"description": ""
		};

		fetch(`${apiUrlStore}quiz`, {
			method: 'POST',
			mode: 'cors',
			headers: {
				'Content-Type': 'application/JSON'
			},
			body: JSON.stringify(data)
		}).then(_ => {
			updateQuizzes();
			displayNotification(
					'Successfully added quiz.',
					notificationStatus.SUCCESS,
					notificationPosition.BOTTOM_LEFT);
		}).catch(_ => {
			displayNotification(
					'Failed to add quiz.',
					notificationStatus.DANGER,
					notificationPosition.BOTTOM_LEFT);
		});
	}

	/**
	 * Saves the changes to the quiz in the database.
	 * @param {Object} localQuiz The quiz that should be updated.
	 */
	function updateQuiz(localQuiz) {
		fetch(`${apiUrlStore}quiz/${localQuiz.id}`, {
			method: 'PUT',
			mode: 'cors',
			headers: {
				'Content-Type': 'application/JSON'
			},
			body: JSON.stringify(localQuiz)
		}).then(_ => {
			displayNotification(
					'Saved quiz changes.',
					notificationStatus.SUCCESS,
					notificationPosition.BOTTOM_LEFT);
			updateEditedQuiz(localQuiz.id);
			updatePlayableQuizzes();
		}).catch(_ => {
			displayNotification(
					'Failed to save quiz changes.',
					notificationStatus.DANGER,
					notificationPosition.BOTTOM_LEFT);
		});
	}


	/**
	 * Reverts all the changes from an edited quiz when they press the respective button.
	 *
	 * @param {Object} quiz The quiz that should be reverted.
	 */
	function undoQuizChanges(quiz) {
		editedQuizzes.forEach((quizItem, i) => {
			if (quizItem.id === quiz.id) {
				editedQuizzes[i] = passByVal(quizzes[i]);
			}
		});
	}

	/**
	 * Deletes the quiz from the database and updates the local quizzes once the request is done.
	 *
	 * @param {string} quizId The id of the quiz that should be deleted.
	 */
	function deleteQuiz(quizId) {
		fetch(`${apiUrlStore}quiz/${quizId}`, {
			method: 'DELETE',
			mode: 'cors'
		}).then(_ => {
			updateQuizzes();
			displayNotification(
					'Quiz successfully deleted.',
					notificationStatus.SUCCESS,
					notificationPosition.BOTTOM_LEFT);
			updatePlayableQuizzes();
		}).catch(_ => {
			displayNotification(
					'Failed to delete quiz.',
					notificationStatus.DANGER,
					notificationPosition.BOTTOM_LEFT);
		});
	}

	/**
	 * Adds a question to the given quiz.
	 *
	 * @param {string} quizId The id of the quiz to which the question should be added.
	 */
	function createQuestion(quizId) {
		editedQuizzes.forEach((quiz, i, quizzes) => {
			if (quiz.id === quizId) {
				editedQuizzes[i].questions = passByVal([...quizzes[i].questions, {
					"question": "",
					"answers": [
						{
							"answer": "",
							"correct": true
						},
						{
							"answer": "",
							"correct": false
						},
						{
							"answer": "",
							"correct": false
						},
						{
							"answer": "",
							"correct": false
						}
					]
				}]);
			}
		});
	}

	/**
	 * Deletes the question specified by the quiz id and the question index.
	 *
	 * @param {string} quizId The id of the quiz from which the question should be deleted.
	 * @param {number} questionIndex The index of the question that should be deleted from the quiz.
	 */
	function deleteQuestion(quizId, questionIndex) {
		editedQuizzes.forEach((quiz, i) => {
			if (quiz.id === quizId) {
				editedQuizzes[i].questions = passByVal(editedQuizzes[i].questions).filter((quiz, i) => i !== questionIndex);
			}
		});
	}

	/**
	 * Flip the boolean associated with the answer clicked.
	 *
	 * @param {string} quizId The id of the quiz of which the boolean of an answer of a question should be flipped.
	 * @param {number} questionIndex The index of the question of which the boolean of an answer should be flipped.
	 * @param {number} answerIndex The index of the answer of which the boolean should be flipped.
	 */
	function negateAnswerBool(quizId, questionIndex, answerIndex) {
		editedQuizzes.forEach((quiz, i) => {
			if (quiz.id === quizId) {
				editedQuizzes[i].questions[questionIndex].answers[answerIndex].correct = passByVal(!quiz.questions[questionIndex].answers[answerIndex].correct);
			}
		});
	}

	/**
	 * Updates the quizzes with data from the backend.
	 *
	 * @param {Array} [data=[]] Optional parameter, which if set can be used to reset the quizzes.
	 */
	function updateQuizzes(data = []) {
		if (data.length !== 0) {
			resetAllQuizzes(data);
		} else {
			fetch(`${apiUrlStore}quiz`, {
				method: 'GET',
				mode: 'cors'
			}).then(result => result.json()).then(data => {
				resetAllQuizzes(data);
			}).catch(_ => {
				console.error('Failed to update edited quizzes.');
			});
		}
	}

	/**
	 * Updates the quizzes with data from the backend.
	 *
	 * @param {string} quizId The id of the quiz that should be reset.
	 * @param {Array} [data=[]] Optional parameter, which if set can be used to reset the quiz.
	 */
	function updateEditedQuiz(quizId, data = []) {
		if (data.length > 0) {
			resetSingleQuiz(data);
		} else {
			fetch(`${apiUrlStore}quiz`, {
				method: 'GET',
				mode: 'cors'
			}).then(result => result.json()).then(data => {
				resetSingleQuiz(data, quizId);
			}).catch(_ => {
				displayNotification(
						'Failed to update quizzes.',
						notificationStatus.DANGER,
						notificationPosition.BOTTOM_LEFT);
			});
		}
	}

	/**
	 * Updates the playable quizzes store.
	 */
	function updatePlayableQuizzes() {
		fetch(`${apiUrlStore}quiz`, {
			method: 'GET',
			mode: 'cors'
		}).then(result => result.json()).then(data => {
			playableQuizzes.set({
				available: playableQuizzesAvailable(data),
				requestAttempted: true
			})
		}).catch(_ => console.error('Failed to update playAbleQuizzes.'));
	}

	/**
	 * Displays a notification on the screen.
	 *
	 * @param {string} message The message to be displayed in the notification.
	 * @param {Object} statusEnum The type of notification to be displayed (primary, success, warning, danger).
	 * @param {Object} positionEnum The position of the notification to be displayed.
	 * @param {number} [timeout=1000] The time until the notification automatically dismisses.
	 */
	function displayNotification(message, statusEnum, positionEnum, timeout = 1000) {
		let status;
		let position;

		switch (statusEnum) {
			case notificationStatus.PRIMARY:
				status = 'primary';
				break;
			case notificationStatus.SUCCESS:
				status = 'success';
				break;
			case notificationStatus.WARNING:
				status = 'warning';
				break;
			case notificationStatus.DANGER:
				status = 'danger';
				break;
			default:
				console.error('Unknown status type!')
		}
		switch (positionEnum) {
			case notificationPosition.TOP_LEFT:
				position = 'top-left';
				break;
			case notificationPosition.TOP_CENTER:
				position = 'top-center';
				break;
			case notificationPosition.TOP_RIGHT:
				position = 'top-right';
				break;
			case notificationPosition.BOTTOM_LEFT:
				position = 'bottom-left';
				break;
			case notificationPosition.BOTTOM_CENTER:
				position = 'bottom-center';
				break;
			case notificationPosition.BOTTOM_RIGHT:
				position = 'bottom-right';
				break;
			default:
				console.error('Unknown position type!')
		}

		UIkit.notification({
			message,
			status,
			pos: position,
			timeout
		});
	}

	const notificationStatus = {
		PRIMARY: 1,
		SUCCESS: 2,
		WARNING: 3,
		DANGER: 4
	};
	const notificationPosition = {
		TOP_LEFT: 1,
		TOP_CENTER: 2,
		TOP_RIGHT: 3,
		BOTTOM_LEFT: 4,
		BOTTOM_CENTER: 5,
		BOTTOM_RIGHT: 6
	};
	const requestMethods = {
		GET: 1,
		PUT: 2,
		POST: 3,
		DELETE: 4
	};
</script>

<style>
	.flip-icon {
		-moz-transform: scale(-1, 1);
		-webkit-transform: scale(-1, 1);
		-o-transform: scale(-1, 1);
		-ms-transform: scale(-1, 1);
		transform: scale(-1, 1);
	}
</style>

<div class="uk-container uk-container-small uk-margin-xlarge-bottom">
	<!-- The button to create a new quiz -->
	<button on:click={createQuiz} class="uk-button uk-button-default uk-border-rounded" uk-tooltip="Add quiz">
		<span class="uk-margin-small-top uk-margin-small-bottom" uk-icon="plus"></span>
	</button>

	<!-- The hint explaining when quizzes are playable -->
	<PlayableQuizHint animationY={animationConf.y} animationDuration={animationConf.duration}/>

	<div class="uk-card uk-card-default uk-card-body uk-border-rounded">
		<!-- The quizzes accordion-->
		<ul uk-accordion>
            {#each editedQuizzes as quiz, i}
				<li class="{i < editedQuizzes.length - 1 ? 'uk-margin-medium' : ''}"
				    transition:fly="{{ y: -animationConf.y, duration: animationConf.duration }}">
					<a class="uk-accordion-title">
                        {quiz.title !== '' ? quiz.title : "No Title"}
                        {#if unidenticalQuizzes[i]}
							<span class="uk-text-small uk-text-warning uk-text-right">Unsaved Changes</span>
                        {/if}
					</a>
					<div class="uk-accordion-content">
						<div>
							<div class="uk-grid-collapse" uk-grid>
								<!-- The quiz title and description fields -->
								<div class="uk-width-expand">
									<div class="uk-margin-small">
										<input bind:value={quiz.title} class="uk-input uk-form-large uk-border-rounded"
										       type="text"
										       placeholder="Quiz title">
									</div>
									<div class="uk-margin-small">
										<input bind:value={quiz.description} class="uk-input uk-border-rounded"
										       type="text"
										       placeholder="Description">
									</div>
								</div>
								<!-- The quiz action buttons (delete, save, and reset) -->
								<div class="uk-width-auto">
									<div class="uk-grid-collapse uk-margin-small-left" uk-grid>
										<div class="uk-width-auto">
											<button on:click={() => deleteQuiz(quiz.id)} uk-tooltip="Delete quiz"
											        class="uk-button uk-button-default uk-border-rounded">
                                            <span class="uk-text-danger uk-margin-small-top uk-margin-small-bottom"
                                                  uk-icon="trash"></span>
											</button>
											<br>
											<button on:click={() => undoQuizChanges(quiz)} uk-tooltip="Undo changes"
											        class="uk-button uk-button-default uk-border-rounded uk-margin-small">
                                            <span class="uk-margin-small-top flip-icon"
                                                  uk-icon="refresh"></span>
											</button>
										</div>
										<div class="uk-width-auto">
											<div class="uk-margin-small-left">
												<button on:click={() => updateQuiz(quiz)} uk-tooltip="Save quiz changes"
												        class="uk-button uk-button-default uk-border-rounded">
                                            <span class="uk-text-success uk-margin-small-top uk-margin-small-bottom"
                                                  uk-icon="check"></span>
												</button>
											</div>
										</div>
									</div>
								</div>
							</div>
						</div>
						<hr>
						<div class="uk-margin-medium-top">
                            {#each quiz.questions as question, j}
								<div class="uk-margin{j < quiz.questions.length - 1 ? '-medium' : ''}"
								     transition:fly="{{ y: -animationConf.y, duration: animationConf.duration }}">
									<div class="uk-grid-collapse" uk-grid>
										<!-- The question field -->
										<div class="uk-width-expand">
											<input bind:value={question.question} class="uk-input uk-border-rounded"
											       type="text"
											       placeholder="Question">
										</div>
										<!-- The button question delete -->
										<div class="uk-width-auto">
											<div class="uk-margin-small-left">
												<button class="uk-button uk-button-default uk-text-danger uk-border-rounded"
												        uk-tooltip="Delete question"
												        on:click={() => deleteQuestion(quiz.id, j)}>
													<span uk-icon="icon: trash"></span>
												</button>
											</div>
										</div>
									</div>
									<!-- The answers -->
									<div class="uk-grid-small" uk-grid>
                                        {#each question.answers as answer, k}
											<div class="uk-width-1-2">
												<div class="uk-inline uk-width-1-1">
                                                    {#if answer.correct}
														<a on:click={() => negateAnswerBool(quiz.id, j, k)}
														   class="uk-form-icon uk-text-success uk-form-icon-flip"
														   uk-icon="icon: check"
														   uk-tooltip="Answer is correct"
														   transition:fade="{{ duration: animationConf.duration }}"><span></span></a>
                                                    {:else}
														<a on:click={() => negateAnswerBool(quiz.id, j, k)}
														   class="uk-form-icon uk-text-danger uk-form-icon-flip"
														   uk-icon="icon: close"
														   uk-tooltip="Answer is wrong"
														   transition:fade="{{ duration: animationConf.duration }}"><span></span></a>
                                                    {/if}
													<input bind:value={answer.answer} class="uk-input uk-border-rounded"
													       type="text"
													       placeholder="Answer">
												</div>
											</div>
                                        {/each}
									</div>
								</div>
                            {/each}
						</div>

						<!-- The button to add a question -->
						<div class="uk-text-center">
							<button on:click={() => createQuestion(quiz.id)}
							        class="uk-button uk-button-default uk-border-rounded"
							        uk-tooltip="Add question">
                                    <span class="uk-margin-small-top uk-margin-small-bottom"
                                          uk-icon="plus"></span>
							</button>
						</div>
					</div>
				</li>
            {:else}
			<!-- The alerts that are displayed in case there either aren't any quizzes or the server is unreachable -->
                {#if connectionSuccessful}
					<div class="uk-alert-primary"
					     transition:fly="{{ y: -animationConf.y, duration: animationConf.duration }}"
					     uk-alert>
						<p>You have not created any quizzes yet. To start, click the <code class="uk-alert-primary">ADD
							QUIZ</code> button.</p>
					</div>
                {:else}
					<div class="uk-alert-danger" uk-alert>
						<p>No connection could be established with the server. Please try again later.</p>
					</div>
                {/if}
            {/each}
		</ul>
	</div>
</div>