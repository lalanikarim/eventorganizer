@extends('layouts.app')

@section('content')
<div class="container">
    <div class="row">
        <div class="col-md-6">
            <div>
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title">Events</h5>
                    @if (session('status'))
                            <div class="alert alert-success" role="alert">
                                {{ session('status') }}
                            </div>
                        @endif
                            @include('events.list',['events' => $events])
                    </div>

                </div>
            </div>
        </div>

        <div class="col-md-6">
            <div>
                <div class="card">
                    <div class="card-body">
                        <h5 class="card-title">By Category</h5>
                        <table class="table">
                            <thead>
                            <tr>
                                @foreach($bycategory as $category)
                                    <th>
                                        @switch($category['category'] ?? "n")
                                            @case("n")
                                            Not Specified
                                            @break
                                            @case("a")
                                            Adult
                                            @break
                                            @case("s")
                                            Student
                                            @break
                                            @case("r")
                                            Senior
                                            @break
                                        @endswitch
                                    </th>
                                @endforeach
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                @foreach($bycategory as $category)
                                    <td>{{number_format($category->count/$total * 100,2)}}%</td>
                                @endforeach
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <div class="card top-buffer">
                    <div class="card-body">
                        <h5 class="card-title">By Gender</h5>
                        <table class="table">
                            <thead>
                            <tr>
                                @foreach($bygender as $gender)
                                    <th>
                                        @switch($gender['sex'] ?? "n")
                                            @case("n")
                                            Not Specified
                                            @break
                                            @case("f")
                                            Female
                                            @break
                                            @case("m")
                                            Male
                                            @break
                                        @endswitch
                                    </th>
                                @endforeach
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                @foreach($bygender as $gender)
                                    <td>{{number_format($gender->count/$total * 100,2)}}%</td>
                                @endforeach
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
                <div class="card top-buffer">
                    <div class="card-body">
                        <h5 class="card-title">By Group</h5>
                        <table class="table">
                            <thead>
                            <tr>
                                <th>Name</th>
                                <th>Total</th>
                            </tr>
                            </thead>
                            <tbody>
                            @foreach($bygroupid as $group)
                                <tr>
                                    <td>{{$group->groupId ?? "None Specified"}}</td>
                                    <td>{{number_format($group->count/$total * 100,2)}}%</td>
                                </tr>
                            @endforeach
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>

</div>
@endsection
