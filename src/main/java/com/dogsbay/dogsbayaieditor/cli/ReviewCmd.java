/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dogsbay.dogsbayaieditor.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ReviewAcceptCommand;
import com.dogsbay.dogsbayaieditor.commands.ReviewCommentCommand;
import com.dogsbay.dogsbayaieditor.commands.ReviewListCommand;
import com.dogsbay.dogsbayaieditor.commands.ReviewRejectCommand;
import com.dogsbay.dogsbayaieditor.commands.results.ProposalInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ReviewResult;

/**
 * Review proposals from the command line, on the file on disk. If the
 * editor has the file open with unsaved changes, decide there instead (or
 * through the editor connection), so the two do not disagree.
 */
@Command(name = "review", description = "List, accept, reject or comment on agent proposals in a document",
        subcommands = {ReviewCmd.List.class, ReviewCmd.Accept.class, ReviewCmd.Reject.class, ReviewCmd.Comment.class})
public class ReviewCmd implements Callable<Integer> {

    @Override
    public Integer call() {
        picocli.CommandLine.usage(this, System.out);
        return 2;
    }

    @Command(name = "list", description = "List the proposals in a document")
    static class List implements Callable<Integer> {
        @Parameters(index = "0", description = "The document")
        private String file;
        @Option(names = "--author", description = "Only this author's proposals (e.g. ai:claude-acp)")
        private String author;

        @Override
        public Integer call() throws Exception {
            var result = new HeadlessExecutor().execute(new ReviewListCommand(file, author), USER);
            if (result.isEmpty()) {
                System.out.println("No proposals.");
                return 0;
            }
            for (ProposalInfo p : result) {
                System.out.printf("%-16s %-8s %-18s line %-5d <%s> %s%n", p.id(), p.kind(), p.author(), p.line(),
                        p.element(), p.text().length() > 70 ? p.text().substring(0, 70) + "…" : p.text());
            }
            System.out.println(result.size() + " proposal(s).");
            return 0;
        }
    }

    /** The person running the CLI: decisions and comments carry their identity, never "user:unknown". */
    static final com.dogsbay.agent.session.AgentSession USER = new AgentSessionRegistry().user();

    abstract static class Decide implements Callable<Integer> {
        @Parameters(index = "0", description = "The document")
        String file;
        @Parameters(index = "1", arity = "0..1", description = "The proposal id from 'review list'")
        String id;
        @Option(names = "--all", description = "Every change (comments are left alone)")
        boolean all;
        @Option(names = "--author", description = "With --all: only this author's changes")
        String author;

        abstract ReviewResult run(HeadlessExecutor executor) throws Exception;

        @Override
        public Integer call() throws Exception {
            ReviewResult r = run(new HeadlessExecutor());
            System.out.println(r.message());
            return 0;
        }
    }

    @Command(name = "accept", description = "Accept a proposal by id, or --all")
    static class Accept extends Decide {
        @Override
        ReviewResult run(HeadlessExecutor executor) throws Exception {
            return executor.execute(new ReviewAcceptCommand(file, id, author, all), USER);
        }
    }

    @Command(name = "reject", description = "Reject a proposal by id, or --all")
    static class Reject extends Decide {
        @Override
        ReviewResult run(HeadlessExecutor executor) throws Exception {
            return executor.execute(new ReviewRejectCommand(file, id, author, all), USER);
        }
    }

    @Command(name = "comment", description = "Leave a draft comment after some text or inside an element")
    static class Comment implements Callable<Integer> {
        @Parameters(index = "0", description = "The document")
        private String file;
        @Parameters(index = "1", description = "The comment text")
        private String text;
        @Option(names = "--after", description = "Place it right after the first occurrence of this text")
        private String afterText;
        @Option(names = "--in", description = "Or as the first child of the element with this id")
        private String elementId;

        @Override
        public Integer call() throws Exception {
            var r = new HeadlessExecutor().execute(new ReviewCommentCommand(file, afterText, elementId, text), USER);
            System.out.println(r.message());
            return 0;
        }
    }
}
